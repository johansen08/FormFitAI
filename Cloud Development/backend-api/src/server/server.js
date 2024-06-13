// Load environment variables from .env file
require("dotenv").config();

// Import Hapi framework and routes
const Hapi = require("@hapi/hapi");
const routes = require("../server/routes");

// Self-invoking async function to start the server
(async () => {
  // Create a new Hapi server instance
  const server = Hapi.server({
    port: 8080, // Server port
    host: "0.0.0.0", // Server host
    routes: {
      cors: {
        origin: ["*"], // Enable CORS for all origins
      }
    },
  });

  // Add routes to the server
  server.route(routes);

  // Start the server
  await server.start();
  // Log server start information
  console.log(`Server started at: ${server.info.uri}`);
})();
