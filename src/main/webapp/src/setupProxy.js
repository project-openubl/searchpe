const { createProxyMiddleware } = require("http-proxy-middleware");

module.exports = function (app) {
  app.use(
    "/templates",
    createProxyMiddleware({
      target: "http://localhost:8080",
    })
  );
};
