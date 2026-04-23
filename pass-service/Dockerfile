# certs/ is NOT copied into the image — it's mounted at runtime via docker-compose volume
# This prevents Apple certificates from being baked into the Docker image

FROM node:20-alpine
WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

COPY index.js .
COPY model/ ./model/

# Create empty certs directory — will be populated by volume mount
RUN mkdir -p certs

EXPOSE 3001

# Run as non-root for security
USER node

CMD ["node", "index.js"]
