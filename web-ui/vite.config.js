import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@client": path.resolve(__dirname, "../src/client"),
      "@shared": path.resolve(__dirname, "../src/shared"),
      "mediasoup-client": path.resolve(__dirname, "node_modules/mediasoup-client"),
    },
  },
  server: {
    port: 5173,
  },
});
