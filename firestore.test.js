const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("fs");

const PROJECT_ID = "demo-no-project";
const emulatorHost = "127.0.0.1";
const emulatorPort = 8085;

const ALICE_UID = "alice_customer";
const BOB_UID = "bob_customer";
const MECHANIC_UID = "mechanic_ramesh";

let testEnv;

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create their own profile as customer", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "customer",
      displayName: "Alice",
      phone: "+91 9876543210",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot elevate role to admin on profile create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "admin",
      displayName: "Alice",
    })
  );
});

test("Authenticated user: cannot create profile for another user ID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      role: "customer",
      displayName: "Bob",
    })
  );
});

test("Customer: can create a service request for themselves", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_1").set({
      customerId: ALICE_UID,
      vehicleType: "BIKE",
      issueCategory: "Flat Tyre",
      description: "Rear tire puncture near flyover",
      status: "REQUESTED",
      assignedMechanicId: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Customer: cannot create request with someone else's customerId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("serviceRequests").doc("req_fraud_1").set({
      customerId: BOB_UID,
      vehicleType: "CAR",
      issueCategory: "Battery Jumpstart",
      description: "Testing fraud",
      status: "REQUESTED",
    })
  );
});

test("Customer: can cancel their own service request", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("serviceRequests").doc("req_alice_2").set({
      customerId: ALICE_UID,
      vehicleType: "CAR",
      issueCategory: "Battery",
      status: "REQUESTED",
      assignedMechanicId: null,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_2").update({
      status: "CANCELLED",
      customerId: ALICE_UID,
    })
  );
});

test("Customer: cannot cancel Bob's service request", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("serviceRequests").doc("req_bob_1").set({
      customerId: BOB_UID,
      vehicleType: "CAR",
      issueCategory: "Brakes",
      status: "REQUESTED",
      assignedMechanicId: null,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("serviceRequests").doc("req_bob_1").update({
      status: "CANCELLED",
      customerId: BOB_UID,
    })
  );
});

test("Customer: can submit a review for completed service", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("reviews").doc("rev_alice_1").set({
      requestId: "req_alice_2",
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      rating: 5,
      comment: "Super fast and helpful!",
      createdAt: new Date(),
    })
  );
});

test("Customer: can query registered mechanics by role while unfiltered user listing is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(MECHANIC_UID).set({
      uid: MECHANIC_UID,
      role: "mechanic",
      displayName: "Ramesh Kumar",
      isVerified: true,
      isOnline: true,
      isAvailable: true,
      isBlocked: false,
      latitude: 28.615,
      longitude: 77.211,
      serviceRadiusKm: 10,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      role: "customer",
      displayName: "Bob Customer",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").where("role", "==", "mechanic").get()
  );

  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").where("role", "==", "mechanic").get()
  );
});

test("Customer: exact Kotlin submitEmergencyRequest, partial cancelActiveRequest, and completeJob payloads succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const nowPast = new Date(Date.now() - 1000);

  // 1. Exact submitEmergencyRequest payload from FirestoreEmergencyRepository.kt
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_kotlin_1").set({
      customerId: ALICE_UID,
      vehicleType: "CAR",
      vehicleMakeModel: "Maruti Suzuki Swift Dzire",
      registrationNumber: "",
      issueCategory: "Battery Jump-Start",
      description: "",
      photoUri: "",
      location: {
        latitude: 28.6139,
        longitude: 77.2090,
        address: "Connaught Place, New Delhi",
        landmark: "",
        city: "New Delhi",
        state: "Delhi",
        pincode: "110001",
        isGpsAccurate: false,
      },
      status: "REQUESTED",
      assignedMechanicId: null,
      assignedMechanicName: null,
      assignedMechanicPhone: null,
      baseInspectionFee: 399,
      finalPartsFee: 0,
      totalPaid: 0,
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // 2. Exact partial update from cancelActiveRequest (does NOT resend customerId)
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_kotlin_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 3. Exact partial update from completeJob + review creation
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_kotlin_1").update({
      status: "COMPLETED",
      ratingGiven: 5.0,
      feedbackComment: "Prompt support",
      totalPaid: 399,
      paymentMethod: "UPI",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(
    aliceDb.collection("reviews").doc("rev_kotlin_1").set({
      requestId: "req_kotlin_1",
      customerId: ALICE_UID,
      mechanicId: "mechanic_assigned",
      rating: 5.0,
      comment: "Prompt support",
      paymentMethod: "UPI",
      createdAt: nowPast,
    })
  );
});

test("Customer: get() on non-existent serviceRequest returns cleanly while Bob's assigned request is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("serviceRequests").doc("req_bob_assigned").set({
      customerId: BOB_UID,
      vehicleType: "CAR",
      issueCategory: "Towing",
      status: "ASSIGNED",
      assignedMechanicId: MECHANIC_UID,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_does_not_exist").get()
  );
  await assertFails(
    aliceDb.collection("serviceRequests").doc("req_bob_assigned").get()
  );
});

test("Mechanic Pro: mechanic can register, update live GPS without resending uid, list/accept/progress requests, and read customer profile", async () => {
  const mechDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // 1. Mechanic can create own /users profile with role 'mechanic'
  await assertSucceeds(
    mechDb.collection("users").doc(MECHANIC_UID).set({
      uid: MECHANIC_UID,
      role: "mechanic",
      displayName: "Ramesh Kumar",
      isVerified: true,
      isOnline: true,
      isAvailable: true,
      latitude: 19.0760,
      longitude: 72.8777,
    })
  );

  // 2. Mechanic can partially update online status & coordinates without resending uid or role
  await assertSucceeds(
    mechDb.collection("users").doc(MECHANIC_UID).update({
      isOnline: false,
      latitude: 19.0801,
      longitude: 72.8812,
    })
  );

  // 3. Same test account can switch between customer and mechanic, but NEVER elevate to admin
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "customer",
      displayName: "Alice",
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "mechanic",
      isOnline: true,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
    })
  );

  // 4. Customer creates an OPEN / REQUESTED job; Mechanic lists all requests, reads customer profile, accepts & progresses job
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_mech_flow_1").set({
      customerId: ALICE_UID,
      vehicleType: "CAR",
      issueCategory: "Engine Trouble",
      status: "REQUESTED",
      assignedMechanicId: null,
    })
  );

  await assertSucceeds(mechDb.collection("serviceRequests").get());
  await assertSucceeds(mechDb.collection("users").doc(ALICE_UID).get());

  // Mechanic claims unassigned request
  await assertSucceeds(
    mechDb.collection("serviceRequests").doc("req_mech_flow_1").update({
      assignedMechanicId: MECHANIC_UID,
      assignedMechanicName: "Ramesh Kumar",
      status: "ACCEPTED",
    })
  );

  // Mechanic updates progress to EN_ROUTE and COMPLETED
  await assertSucceeds(
    mechDb.collection("serviceRequests").doc("req_mech_flow_1").update({
      status: "EN_ROUTE",
    })
  );

  // Mechanic can also write to /mechanics, /earnings, /notifications, and chat messages
  await assertSucceeds(
    mechDb.collection("mechanics").doc(MECHANIC_UID).set({
      uid: MECHANIC_UID,
      displayName: "Ramesh Kumar",
      isOnline: true,
      isVerified: true,
    })
  );
  await assertSucceeds(
    mechDb.collection("earnings").doc("earn_1").set({
      mechanicId: MECHANIC_UID,
      amount: 399,
      requestId: "req_mech_flow_1",
    })
  );
  await assertSucceeds(
    mechDb.collection("serviceRequests").doc("req_mech_flow_1").collection("messages").doc("msg_1").set({
      senderId: MECHANIC_UID,
      message: "I am 5 minutes away.",
      isCustomer: false,
    })
  );
});

test("Multi-account isolation: Customer B cannot read, list, hijack, or spy on Customer A's open serviceRequest, messages, or audit events", async () => {
  const assert = require("node:assert/strict");
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Seed Customer profiles
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "customer",
      displayName: "Alice",
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      role: "customer",
      displayName: "Bob",
    })
  );

  // User A creates a REQUESTED emergency request, chat message, and audit event
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_private").set({
      customerId: ALICE_UID,
      vehicleType: "CAR",
      issueCategory: "Battery Jump-Start",
      status: "REQUESTED",
      assignedMechanicId: null,
    })
  );
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_private").collection("messages").doc("msg_a1").set({
      senderId: ALICE_UID,
      message: "Near gate 2",
      isCustomer: true,
    })
  );
  await assertSucceeds(
    aliceDb.collection("requestEvents").doc("evt_a1").set({
      requestId: "req_alice_private",
      actorId: ALICE_UID,
      eventType: "REQUEST_CREATED",
      status: "REQUESTED",
    })
  );

  // User B tries to get/list User A's REQUESTED service request -> MUST FAIL
  await assertFails(bobDb.collection("serviceRequests").doc("req_alice_private").get());
  await assertFails(bobDb.collection("serviceRequests").where("customerId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("serviceRequests").get());

  // User B tries to hijack User A's unassigned request as assignedMechanicId -> MUST FAIL (Bob is a customer, not mechanic)
  await assertFails(
    bobDb.collection("serviceRequests").doc("req_alice_private").update({
      assignedMechanicId: BOB_UID,
      status: "ACCEPTED",
    })
  );

  // User B tries to read or post into User A's private chat messages or audit events -> MUST FAIL
  await assertFails(bobDb.collection("serviceRequests").doc("req_alice_private").collection("messages").get());
  await assertFails(
    bobDb.collection("serviceRequests").doc("req_alice_private").collection("messages").doc("msg_b_spy").set({
      senderId: BOB_UID,
      message: "Spoofed message",
      isCustomer: true,
    })
  );
  await assertFails(bobDb.collection("requestEvents").doc("evt_a1").get());
  await assertFails(bobDb.collection("requestEvents").where("requestId", "==", "req_alice_private").get());

  // User B creates their own request and sees ONLY User B's record
  await assertSucceeds(
    bobDb.collection("serviceRequests").doc("req_bob_private").set({
      customerId: BOB_UID,
      vehicleType: "BIKE",
      issueCategory: "Flat Tyre",
      status: "REQUESTED",
      assignedMechanicId: null,
    })
  );
  const bobOwnedSnap = await assertSucceeds(
    bobDb.collection("serviceRequests").where("customerId", "==", BOB_UID).get()
  );
  assert.equal(bobOwnedSnap.docs.length, 1);
  assert.equal(bobOwnedSnap.docs[0].id, "req_bob_private");

  // Switching back to User A restores ONLY User A's records
  const aliceOwnedSnap = await assertSucceeds(
    aliceDb.collection("serviceRequests").where("customerId", "==", ALICE_UID).get()
  );
  assert.equal(aliceOwnedSnap.docs.length, 1);
  assert.equal(aliceOwnedSnap.docs[0].id, "req_alice_private");
  await assertFails(aliceDb.collection("serviceRequests").doc("req_bob_private").get());
});

test("Chats collection: restricted to participants while unauthorized customers are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const mechDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      participants: [ALICE_UID, MECHANIC_UID],
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("m1").set({
      senderId: ALICE_UID,
      text: "Hello",
    })
  );
  await assertSucceeds(
    mechDb.collection("chats").doc("chat_1").collection("messages").get()
  );

  await assertFails(bobDb.collection("chats").doc("chat_1").get());
  await assertFails(bobDb.collection("chats").doc("chat_1").collection("messages").get());
  await assertFails(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("m2").set({
      senderId: BOB_UID,
      text: "Intruder",
    })
  );
});




