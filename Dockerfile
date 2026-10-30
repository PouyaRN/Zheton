# Multi-stage build for Zheton PWA on Cloud Run
FROM node:20-alpine AS builder

WORKDIR /build

# Copy PWA package manifests first for caching
COPY pwa/package*.json ./pwa/
RUN cd pwa && npm install

# Copy PWA source and build
COPY pwa ./pwa
RUN cd pwa && npm run build

# Lightweight static server stage
FROM nginx:alpine

# Copy built assets
COPY --from=builder /build/pwa/dist /usr/share/nginx/html

# Cloud Run expects container to listen on port 8080
RUN sed -i 's/80;/8080;/g' /etc/nginx/conf.d/default.conf

# Support SPA routing fallback
RUN sed -i 's|try_files $uri $uri/ =404;|try_files $uri $uri/ /index.html;|g' /etc/nginx/conf.d/default.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
