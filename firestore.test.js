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
