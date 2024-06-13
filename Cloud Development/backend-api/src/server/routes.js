// Import necessary handlers and middleware
const { register, login, updateUser, deleteUser, getUser, updateProfile, getProfile } = require("../server/userHandler");
const { verifyToken } = require("../middleware/authMiddleware");

// Define the routes array with various endpoints
const routes = [
  // Test route for checking the server
  {
    path: '/test',
    method: 'GET',
    handler: (request, h) => {
        const response = h.response({
            status: 'success',
            message: 'testing',
        });
        response.code(200); // Set HTTP status code to 200
        return response; // Return the response object
    }
  },
  // Route for user registration
  {
    method: "POST",
    path: "/register",
    handler: register, // Handler function for user registration
  },
  // Route for user login
  {
    method: "POST",
    path: "/login",
    handler: login, // Handler function for user login
  },
  // Route for updating user information
  {
    method: "PUT",
    path: "/users/{id}", // {id} is a path parameter
    handler: updateUser, // Handler function for updating user
    options: {
      pre: [{ method: verifyToken }], // Middleware to verify token before updating user
    },
  },
  // Route for getting user information
  {
    method: "GET",
    path: "/users/{id}", // {id} is a path parameter
    handler: getUser, // Handler function for getting user information
    options: {
      pre: [{ method: verifyToken }], // Middleware to verify token before fetching user
    },
  },
  // Route for deleting a user
  {
    method: "DELETE",
    path: "/users/{id}", // {id} is a path parameter
    handler: deleteUser, // Handler function for deleting user
    options: {
      pre: [{ method: verifyToken }], // Middleware to verify token before deleting user
    },
  },{
    method: "PUT",
    path: "/profile",
    handler: updateProfile,
    options: {
      pre: [{ method: verifyToken }],
    },
  },
  {
    method: "GET",
    path: "/profile",
    handler: getProfile,
    options: {
      pre: [{ method: verifyToken }],
    },
  },

];

// Export the routes array to be used in server setup
module.exports = routes;
