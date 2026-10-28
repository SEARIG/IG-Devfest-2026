import { readFileSync } from "node:fs";
import { after, afterEach, before, test } from "node:test";
import assert from "node:assert/strict";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";

let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: "agro-ai-34b4a",
    firestore: { rules: readFileSync("firestore.rules", "utf8") },
  });
});

afterEach(async () => environment.clearFirestore());
after(async () => environment.cleanup());

function profile(uid, phone = "+919876543210") {
  return {
    uid,
    name: "Test Farmer",
    phoneNumber: phone,
    preferredLanguage: "en",
    location: { state: "Rajasthan", district: "Jodhpur", village: "" },
    farm: { area: 2, areaUnit: "acre", soilType: "loamy", irrigationType: "seasonal" },
    currentCrops: ["wheat"],
    goals: ["improve_yield"],
    experienceLevel: "beginner",
    onboardingCompleted: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function plotRecord(uid, id = "plot-1") {
  return {
    ownerUid: uid,
    recordId: id,
    revision: 1,
    deleted: false,
    payload: {
      name: "North field",
      area: 2,
      areaUnit: "acre",
      soilType: "loamy",
      irrigationMethod: "seasonal",
      cropHistory: "",
      clientUpdatedAt: 1,
    },
    updatedAt: serverTimestamp(),
  };
}

test("owner profile access succeeds while unauthenticated and cross-account access fail", async () => {
  const owner = environment.authenticatedContext("alice", { phone_number: "+919876543210" }).firestore();
  const outsider = environment.authenticatedContext("bob", { phone_number: "+919876543211" }).firestore();
  const guest = environment.unauthenticatedContext().firestore();
  const ref = doc(owner, "farmers/alice");

  await assertSucceeds(setDoc(ref, profile("alice")));
  await assertSucceeds(getDoc(ref));
  await assertFails(getDoc(doc(outsider, "farmers/alice")));
  await assertFails(getDoc(doc(guest, "farmers/alice")));
  await assertFails(deleteDoc(ref));
});

test("profile ownership and immutable verified phone are enforced", async () => {
  const owner = environment.authenticatedContext("alice", { phone_number: "+919876543210" }).firestore();
  const ref = doc(owner, "farmers/alice");
  await assertFails(setDoc(ref, profile("mallory")));
  await assertSucceeds(setDoc(ref, profile("alice")));
  await assertFails(updateDoc(ref, { phoneNumber: "+919999999999", updatedAt: serverTimestamp() }));
});

test("nested records enforce owner, schema, revisions, and cross-account denial", async () => {
  const owner = environment.authenticatedContext("alice", { phone_number: "+919876543210" }).firestore();
  const outsider = environment.authenticatedContext("bob", { phone_number: "+919876543211" }).firestore();
  const ref = doc(owner, "farmers/alice/plots/plot-1");
  await assertSucceeds(setDoc(ref, plotRecord("alice")));
  await assertFails(getDoc(doc(outsider, "farmers/alice/plots/plot-1")));
  await assertFails(setDoc(doc(owner, "farmers/alice/plots/bad-owner"), plotRecord("bob", "bad-owner")));
  await assertFails(updateDoc(ref, { revision: 3, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(ref, { revision: 2, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(ref, {
    revision: 3,
    deleted: true,
    payload: {},
    updatedAt: serverTimestamp(),
  }));
  await assertFails(deleteDoc(ref));
  const bad = plotRecord("alice", "bad-schema");
  bad.payload.area = -1;
  await assertFails(setDoc(doc(owner, "farmers/alice/plots/bad-schema"), bad));
  const oversized = plotRecord("alice", "oversized");
  oversized.payload.name = "x".repeat(81);
  await assertFails(setDoc(doc(owner, "farmers/alice/plots/oversized"), oversized));
});

test("default deny blocks documents outside the farmer hierarchy", async () => {
  const owner = environment.authenticatedContext("alice", { phone_number: "+919876543210" }).firestore();
  await assertFails(setDoc(doc(owner, "public/example"), { open: true }));
  assert.ok(true);
});
