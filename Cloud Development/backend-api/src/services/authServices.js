const Firestore = require("@google-cloud/firestore");

// Initialize Firestore instance
const db = new Firestore({
  databaseId: "formfitdbs",
});

// Reference to the 'users' collection in Firestore
const usersCollection = db.collection("users");

/**
 * Get a user by email.
 * @param {string} email - The email of the user to find.
 * @returns {Promise<QuerySnapshot>} - A promise that resolves to the query snapshot of the user.
 */
const getUserByEmail = async (email) => {
  return await usersCollection.where("email", "==", email).get();
};

/**
 * Create a new user in the 'users' collection.
 * @param {Object} userData - The data of the user to create.
 * @param {string} userData.name - The name of the user.
 * @param {string} userData.email - The email of the user.
 * @param {string} userData.password - The hashed password of the user.
 * @returns {Promise<DocumentReference>} - A promise that resolves to the document reference of the newly created user.
 */
const createUser = (userData) => {
  return db.collection("users").add(userData);
};

/**
 * Update a user by their document ID.
 * @param {string} id - The document ID of the user to update.
 * @param {Object} user - The new data for the user.
 * @param {string} [user.email] - The new email of the user.
 * @param {string} [user.password] - The new hashed password of the user.
 * @param {string} [user.name] - The new name of the user.
 * @returns {Promise<WriteResult>} - A promise that resolves to the write result of the update operation.
 */
const updateUserById = async (id, user) => {
  const userDoc = usersCollection.doc(id);
  const updateData = {};

  // Only add fields to updateData if they are defined
  if (user.email !== undefined) {
    updateData.email = user.email;
  }

  if (user.password !== undefined) {
    updateData.password = user.password;
  }

  if (user.name !== undefined) {
    updateData.name = user.name;
  }

  return await userDoc.update(updateData);
};

/**
 * Delete a user by their document ID.
 * @param {string} id - The document ID of the user to delete.
 * @returns {Promise<WriteResult>} - A promise that resolves to the write result of the delete operation.
 */
const deleteUserById = async (id) => {
  const userDoc = usersCollection.doc(id);
  return await userDoc.delete();
};

/**
 * Get a user by their document ID.
 * @param {string} id - The document ID of the user to get.
 * @returns {Promise<DocumentSnapshot>} - A promise that resolves to the document snapshot of the user.
 */
const getUserById = async (id) => {
  const userDoc = usersCollection.doc(id);
  return await userDoc.get();
};

module.exports = { getUserByEmail, createUser, updateUserById, deleteUserById, getUserById };
