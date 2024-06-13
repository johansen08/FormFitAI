const bcrypt = require("bcrypt");
const jwt = require("jsonwebtoken");
const {
  getUserByEmail,
  createUser,
  updateUserById,
  deleteUserById,
  getUserById,
} = require("../services/authServices");
const { JWT_SECRET } = require("../../config");

/**
 * Handler for registering a new user.
 * @param {Object} request - The request object from the server.
 * @param {Object} h - The response object from the server.
 * @returns {Object} - JSON response object containing status and message.
 */
const register = async (request, h) => {
  const { name, email, password } = request.payload;

  try {
    // Check if the user already exists based on email
    const userSnapshot = await getUserByEmail(email);
    if (!userSnapshot.empty) {
      return h.response({
        status: "Fail",
        message: "User already exists",
      }).code(400);
    }

    // Hash the password before storing
    const hashedPassword = await bcrypt.hash(password, 10);
    const userData = { name, email, password: hashedPassword };

    // Create a new user and get the ID
    const userRef = await createUser(userData);
    const id = userRef.id;

    // Update user's ID with the generated ID
    await userRef.update({ id });

    return h.response({
      status: "success",
      message: "User registered successfully",
      user: { id, email, name },
    }).code(201);
  } catch (error) {
    console.error("Error in register:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};

/**
 * Handler for the login process.
 * @param {Object} request - The request object from the server.
 * @param {Object} h - The response object from the server.
 * @returns {Object} - JSON response object containing status, message, and user data.
 */
const login = async (request, h) => {
  const { email, password } = request.payload;

  try {
    // Check if a user with the given email exists
    const userSnapshot = await getUserByEmail(email);
    if (userSnapshot.empty) {
      return h.response({
        status: "Fail",
        message: "Invalid email or password",
      }).code(400);
    }

    const userDoc = userSnapshot.docs[0];
    const user = userDoc.data();

    // Verify the password
    const isValid = await bcrypt.compare(password, user.password);
    if (!isValid) {
      return h.response({
        status: "Fail",
        message: "Invalid email or password",
      }).code(400);
    }

    // Generate JWT token for the user
    const token = jwt.sign({ id: user.id }, JWT_SECRET, { expiresIn: "12h" });
    
    // Update the token in the database for the logged-in user
    await userDoc.ref.update({ token });

    return h.response({
      status: "success",
      message: "Login successful",
      user: { id: userDoc.id, email: user.email, name: user.name, token },
    }).code(200);
  } catch (error) {
    console.error("Error in login:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};

/**
 * Handler for updating user data based on ID.
 * @param {Object} request - The request object from the server.
 * @param {Object} h - The response object from the server.
 * @returns {Object} - JSON response object containing status and message.
 */
const updateUser = async (request, h) => {
  const userId = request.params.id;
  const { name, email, password } = request.payload;

  try {
    const updatedData = request.payload;

    // Hash password if there is a password change
    let hashedPassword = undefined;
    if (updatedData.password) {
      hashedPassword = await bcrypt.hash(updatedData.password, 10);
    }

    // Update user data
    await updateUserById(userId, updatedData);

    return h.response({
      status: "success",
      message: "User updated successfully",
    }).code(200);
  } catch (error) {
    console.error("Error in updateUser:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};

/**
 * Handler for deleting a user based on ID.
 * @param {Object} request - The request object from the server.
 * @param {Object} h - The response object from the server.
 * @returns {Object} - JSON response object containing status and message.
 */
const deleteUser = async (request, h) => {
  const userId = request.params.id;

  try {
    // Delete user based on ID
    await deleteUserById(userId);

    return h.response({
      status: "success",
      message: "User deleted successfully",
    }).code(200);
  } catch (error) {
    console.error("Error in deleteUser:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};

/**
 * Handler for retrieving user information based on ID.
 * @param {Object} request - The request object from the server.
 * @param {Object} h - The response object from the server.
 * @returns {Object} - JSON response object containing status and user data.
 */
const getUser = async (request, h) => {
  const userId = request.params.id;

  try {
    // Get user information based on ID
    const userSnapshot = await getUserById(userId);

    // If user is not found
    if (!userSnapshot.exists) {
      return h.response({
        status: false,
        message: "User not found",
      }).code(404);
    }

    const user = userSnapshot.data();
    delete user.password; // Do not include password in the response

    return h.response({
      status: "success",
      data: user,
    }).code(200);
  } catch (error) {
    console.error("Error in getUser:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }

};

const updateProfile = async (request, h) => {
  const userId = request.user.id;  // Mengambil ID pengguna dari token yang sudah diverifikasi
  

  try {
   
    const { name, gender, weight, height } = request.payload;
    // Update user data
    await updateUserById(userId, { name, gender, weight, height });

    return h.response({
      status: "success",
      message: "Profile updated successfully",
    }).code(200);
  } catch (error) {
    console.error("Error in updateProfile:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};


const getProfile = async (request, h) => {
  const userId = request.auth.credentials.id; // Mengambil ID pengguna dari token yang sudah diverifikasi

  try {
    const userSnapshot = await getUserById(userId);

    if (!userSnapshot.exists) {
      return h.response({
        status: false,
        message: "User not found",
      }).code(404);
    }

    const user = userSnapshot.data();
    delete user.password;

    return h.response({
      status: "success",
      data: user,
    }).code(200);
  } catch (error) {
    console.error("Error in getProfile:", error);
    return h.response({
      status: "Fail",
      message: "Internal Server Error",
    }).code(500);
  }
};

module.exports = { register, login, updateUser, deleteUser, getUser, updateProfile, getProfile };
