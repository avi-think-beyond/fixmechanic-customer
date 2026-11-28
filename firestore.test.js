const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");

const PROJECT_ID = "fixmechanic-firebase";
const emulatorHost = "127.0.0.1";
const emulatorPort = 8085;

const ALICE_UID = "alice_customer";
const BOB_UID = "bob_customer";
const MECHANIC_UID = "mechanic_ramesh";
const ADMIN_UID = "admin_ops";

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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("serviceRequests").get());
  await assertFails(unauthDb.collection("mechanicProfiles").get());
  await assertFails(unauthDb.collection("applicationConfig").doc("current").get());
});

test("Users collection: customer creates own CUSTOMER profile, cannot escalate role/disabled or read Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "CUSTOMER",
      displayName: "Alice",
      email: "alice@example.com",
      phone: "+919876543210",
      disabled: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
  );

  // Updating profile fields without touching role/disabled succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      {
        displayName: "Alice Sharma",
        preferredLanguage: "hi",
        updatedAt: Date.now(),
      },
      { merge: true }
    )
  );

  // Attempting to escalate role or toggle disabled fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ role: "ADMIN" })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ disabled: true })
  );

  // Attempting to create an ADMIN user directly fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      role: "ADMIN",
      displayName: "Mallory",
    })
  );

  // Bob cannot read Alice's user doc
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("ServiceRequests: strict multi-tenant isolation with owner-only direct create and cancel", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("serviceRequests").doc("req_alice_1").set({
      customerId: ALICE_UID,
      assignedMechanicId: MECHANIC_UID,
      vehicleType: "FOUR_WHEELER_CAR",
      serviceCategory: "BATTERY_JUMPSTART",
      status: "EN_ROUTE",
      createdAt: Date.now(),
    });
    await db.collection("serviceRequests").doc("req_bob_1").set({
      customerId: BOB_UID,
      assignedMechanicId: null,
      vehicleType: "TWO_WHEELER",
      serviceCategory: "FLAT_TYRE",
      status: "SEARCHING",
      createdAt: Date.now(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const mechDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();

  // Alice sees ONLY her own serviceRequest
  const aliceSnap = await assertSucceeds(
    aliceDb.collection("serviceRequests").where("customerId", "==", ALICE_UID).get()
  );
  assert.equal(aliceSnap.docs.length, 1);
  assert.equal(aliceSnap.docs[0].id, "req_alice_1");

  // Bob cannot read Alice's request by doc or query
  await assertFails(bobDb.collection("serviceRequests").doc("req_alice_1").get());
  await assertFails(
    bobDb.collection("serviceRequests").where("customerId", "==", ALICE_UID).get()
  );

  // Assigned mechanic can read Alice's assigned request, but not Bob's unassigned request (without MECHANIC role)
  await assertSucceeds(mechDb.collection("serviceRequests").doc("req_alice_1").get());
  await assertFails(mechDb.collection("serviceRequests").doc("req_bob_1").get());

  // Alice CAN create her own request in SEARCHING status with null assignedMechanicId
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_direct").set({
      customerId: ALICE_UID,
      assignedMechanicId: null,
      vehicleType: "TWO_WHEELER",
      serviceCategory: "FLAT_TYRE",
      status: "SEARCHING",
      createdAt: Date.now(),
    })
  );

  // Alice CAN cancel her own request
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_alice_direct").update({
      status: "CANCELLED",
    })
  );

  // Alice CANNOT impersonate Bob or tamper with assignedMechanicId or force COMPLETED status
  await assertFails(
    aliceDb.collection("serviceRequests").doc("req_impersonate_bob").set({
      customerId: BOB_UID,
      status: "SEARCHING",
    })
  );
  await assertFails(
    aliceDb.collection("serviceRequests").doc("req_alice_1").update({
      status: "COMPLETED",
    })
  );
  await assertFails(
    bobDb.collection("serviceRequests").doc("req_alice_1").update({
      status: "CANCELLED",
    })
  );
});

test("MechanicProfiles: authenticated customers can list approved online mechanics; mechanics cannot self-approve", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("mechanicProfiles").doc(MECHANIC_UID).set({
      mechanicId: MECHANIC_UID,
      name: "Ramesh Kumar",
      approvalStatus: "APPROVED",
      isOnline: true,
      isBusy: false,
      serviceRadiusKm: 10,
      currentLocation: { lat: 28.4595, lng: 77.0266, address: "Sector 29 Gurugram" },
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const snap = await assertSucceeds(
    aliceDb
      .collection("mechanicProfiles")
      .where("approvalStatus", "==", "APPROVED")
      .where("isOnline", "==", true)
      .where("isBusy", "==", false)
      .get()
  );
  assert.equal(snap.docs.length, 1);

  const newMechDb = testEnv.authenticatedContext("new_mech").firestore();
  await assertFails(
    newMechDb.collection("mechanicProfiles").doc("new_mech").set({
      mechanicId: "new_mech",
      approvalStatus: "APPROVED",
      isOnline: true,
    })
  );
  await assertSucceeds(
    newMechDb.collection("mechanicProfiles").doc("new_mech").set({
      mechanicId: "new_mech",
      approvalStatus: "PENDING",
      isOnline: false,
      isBusy: false,
    })
  );
});

test("JobChats and jobMessages: isolated strictly to request customer and assigned mechanic", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("jobChats").doc("req_alice_1").set({
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      status: "OPEN",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const mechDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("jobChats").doc("req_alice_1").collection("jobMessages").doc("m1").set({
      senderId: ALICE_UID,
      senderRole: "CUSTOMER",
      text: "Near highway exit 3",
      timestamp: Date.now(),
    })
  );
  await assertSucceeds(
    mechDb.collection("jobChats").doc("req_alice_1").collection("jobMessages").get()
  );

  // Bob cannot read or post in Alice's job chat
  await assertFails(bobDb.collection("jobChats").doc("req_alice_1").get());
  await assertFails(
    bobDb.collection("jobChats").doc("req_alice_1").collection("jobMessages").get()
  );
  await assertFails(
    bobDb.collection("jobChats").doc("req_alice_1").collection("jobMessages").doc("m2").set({
      senderId: BOB_UID,
      text: "Intruder message",
    })
  );
});

test("DeviceTokens and applicationConfig: owner-only token management and authenticated config read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("deviceTokens").doc(`${ALICE_UID}_android`).set({
      userId: ALICE_UID,
      role: "CUSTOMER",
      platform: "android",
      token: "fcm_token_alice",
      updatedAt: Date.now(),
    })
  );
  await assertFails(bobDb.collection("deviceTokens").doc(`${ALICE_UID}_android`).get());
  await assertFails(bobDb.collection("deviceTokens").doc(`${ALICE_UID}_android`).delete());
  await assertSucceeds(aliceDb.collection("deviceTokens").doc(`${ALICE_UID}_android`).delete());

  await assertSucceeds(aliceDb.collection("applicationConfig").doc("current").get());
  await assertFails(
    aliceDb.collection("applicationConfig").doc("current").set({ dispatchRadiusKm: 999 })
  );
});

test("Admin role: authorized for adminAuditLogs and serviceRequests management", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { role: "ADMIN" }).firestore();
  await assertSucceeds(
    adminDb.collection("serviceRequests").doc("req_admin_1").set({
      customerId: ALICE_UID,
      status: "SEARCHING",
    })
  );
  await assertSucceeds(
    adminDb.collection("adminAuditLogs").doc("log_1").set({
      actorId: ADMIN_UID,
      action: "APPROVE_MECHANIC",
      timestamp: Date.now(),
    })
  );
});

test("Direct customer dispatch flow: Google user record, jobChats, jobOffers, and ratings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = Date.now();

  // Direct Google Sign-In creates users/{uid} with CUSTOMER role and google authProvider
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      role: "CUSTOMER",
      displayName: "Alice Google",
      email: "alice@gmail.com",
      phone: "",
      photoUrl: "https://example.com/alice.png",
      authProvider: "google",
      disabled: false,
      profileCompleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Customer creates service request, opens chat room, dispatches offer, and submits rating
  await assertSucceeds(
    aliceDb.collection("serviceRequests").doc("req_flow_1").set({
      requestId: "req_flow_1",
      customerId: ALICE_UID,
      status: "SEARCHING",
      assignedMechanicId: null,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("jobChats").doc("req_flow_1").set({
      requestId: "req_flow_1",
      customerId: ALICE_UID,
      mechanicId: null,
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("jobOffers").doc(`req_flow_1_${MECHANIC_UID}`).set({
      offerId: `req_flow_1_${MECHANIC_UID}`,
      requestId: "req_flow_1",
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      status: "PENDING",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("ratings").doc("req_flow_1").set({
      requestId: "req_flow_1",
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      score: 5.0,
      feedback: "Fast puncture repair",
      paymentMethod: "UPI",
      createdAt: now,
    })
  );
  // Retrying rating submission succeeds for owner, fails for Bob
  await assertSucceeds(
    aliceDb.collection("ratings").doc("req_flow_1").set({
      requestId: "req_flow_1",
      customerId: ALICE_UID,
      mechanicId: MECHANIC_UID,
      score: 5.0,
      feedback: "Fast puncture repair!",
      paymentMethod: "UPI",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("ratings").doc("req_flow_1").set({
      requestId: "req_flow_1",
      customerId: BOB_UID,
      score: 1.0,
    })
  );

  // Deleting a non-existent device token on logout succeeds for owner
  await assertSucceeds(
    aliceDb.collection("deviceTokens").doc(`${ALICE_UID}_android`).delete()
  );
});

