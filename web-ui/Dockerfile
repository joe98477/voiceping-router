FROM node:20-alpine as build
WORKDIR /app
COPY package.json package.json
COPY vite.config.js vite.config.js
COPY index.html index.html
COPY src src
RUN npm install
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
