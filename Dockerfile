FROM node:16-alpine

# Global dependencies
RUN apk --no-cache add --virtual .build-deps \
    g++ gcc libgcc libstdc++ linux-headers git make python3 bash openssh && \
    npm install --quiet node-gyp -g

# App dependencies
WORKDIR /app
COPY package.json ./
RUN npm install

# Remove unneeded dependencies
RUN apk del .build-deps

# Build assets
COPY . ./

# Compile codes to dist
RUN npm run build

# Default runtime configs and helpers
EXPOSE 3000
CMD npm run start
