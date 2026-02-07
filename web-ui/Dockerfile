FROM node:20-alpine as build
WORKDIR /repo/web-ui
ARG VITE_API_BASE
ARG VITE_STARTUP_STATUS
ARG VITE_ROUTER_WS
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_STARTUP_STATUS=$VITE_STARTUP_STATUS
ENV VITE_ROUTER_WS=$VITE_ROUTER_WS
COPY web-ui/package.json package.json
COPY web-ui/package-lock.json package-lock.json
COPY web-ui/vite.config.js vite.config.js
COPY web-ui/index.html index.html
COPY web-ui/src src
# Copy src/client and src/shared for @client/@shared Vite aliases
COPY src/client /repo/src/client
COPY src/shared /repo/src/shared
RUN npm ci
RUN npm run build

FROM nginx:alpine
COPY web-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/web-ui/dist /usr/share/nginx/html
