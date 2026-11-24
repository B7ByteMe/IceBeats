const http = require("http");
const fs = require("fs");
const path = require("path");
const { exec } = require("child_process");

const candidateDirs = [
  path.join(__dirname, "web-icebats"),
  path.join(__dirname, "IceBeats", "web-icebats"),
  path.join(__dirname)
];

let baseDir = candidateDirs.find(d => fs.existsSync(d) && fs.existsSync(path.join(d, "admin", "index.html")));
if (!baseDir) {
  baseDir = candidateDirs[0];
}

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2"
};

function startServer(port) {
  const server = http.createServer((req, res) => {
    try {
      const reqUrl = new URL(req.url, `http://localhost:${port}`);
      let pathname = decodeURIComponent(reqUrl.pathname);

      if (pathname === "/" || pathname === "") pathname = "/admin/index.html";
      if (pathname === "/admin" || pathname === "/admin/") pathname = "/admin/index.html";

      let filePath = path.join(baseDir, pathname);

      if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
        filePath = path.join(filePath, "index.html");
      }

      if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || "application/octet-stream";
        res.writeHead(200, {
          "Content-Type": contentType,
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "no-cache"
        });
        fs.createReadStream(filePath).pipe(res);
      } else {
        res.writeHead(404, { "Content-Type": "text/plain" });
        res.end("404 Not Found");
      }
    } catch (err) {
      res.writeHead(500, { "Content-Type": "text/plain" });
      res.end("500 Internal Server Error");
    }
  });

  server.on("error", (err) => {
    if (err.code === "EADDRINUSE") {
      console.log(`Port ${port} sedang dipakai, mencoba port ${port + 1}...`);
      startServer(port + 1);
    } else {
      console.error("Server error:", err);
    }
  });

  server.listen(port, () => {
    const adminUrl = `http://localhost:${port}/admin/`;
    console.log(`====================================================`);
    console.log(`  IceBeats Admin Dashboard berjalan di:`);
    console.log(`  ${adminUrl}`);
    console.log(`====================================================`);

    exec(`start ${adminUrl}`);
  });
}

startServer(3001);
