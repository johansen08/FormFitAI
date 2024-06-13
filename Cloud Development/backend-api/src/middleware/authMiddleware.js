const jwt = require("jsonwebtoken");
const { JWT_SECRET } = require("../../config");
const { getUserByEmail, getUserById } = require("../services/authServices");

/**
 * Middleware to verify JWT token from the Authorization header.
 * Attaches the user data to the request object if token is valid.
 * 
 * @param {Object} request - Hapi request object
 * @param {Object} h - Hapi response toolkit
 * @returns {Object} Hapi response toolkit continue or takeover
 */
const verifyToken = async (request, h) => {
  // Extract authorization header
  const authHeader = request.headers.authorization;

  // If no authorization header, respond with 401 error
  if (!authHeader) {
    return h.response({
      status: "Fail",
      message: "No token provided",
    }).code(401).takeover();
  }

  // Extract token from the authorization header
  const token = authHeader.split(" ")[1];
  if (!token) {
    return h.response({
      status: "Fail",
      message: "Token format is invalid",
    }).code(401).takeover();
  }

  try {
    // Verify the token using JWT_SECRET
    const decodedToken = jwt.verify(token, JWT_SECRET);
    const userId = decodedToken.id;

    // Fetch user data by ID
    const userSnapshot = await getUserById(userId);
    if (userSnapshot.empty) {
      return h.response({
        status: "Fail",
        message: "User not found",
      }).code(401).takeover();
    }

    // Attach user data to the request object
    const user = userSnapshot.data();
    request.user = { ...user, id: userId };

    // Continue with the request lifecycle
    return h.continue;

  } catch (error) {
    console.error("Token verification failed:", error);

    // Handle specific JWT errors
    if (error.name === "TokenExpiredError") {
      return h.response({
        status: "Fail",
        message: "Token has expired",
      }).code(401).takeover();
    } else if (error.name === "JsonWebTokenError") {
      return h.response({
        status: "Fail",
        message: "Invalid token",
      }).code(401).takeover();
    } else {
      return h.response({
        status: "Fail",
        message: "Authentication error",
      }).code(500).takeover();
    }
  }
};

module.exports = { verifyToken };
