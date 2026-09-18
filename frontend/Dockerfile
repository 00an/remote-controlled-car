# Build configuration
FROM node:20-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# Build installations
COPY package*.json ./
RUN npm ci
COPY . .

# Build
RUN npm run build


# Run configuration
FROM node:20-alpine
WORKDIR /app
EXPOSE 8080
ENV NODE_ENV=production
ENV PORT=8080

# Run installation
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

# Run OKD permissions
RUN mkdir -p /app/.next/cache && \
    chmod -R 777 /app/.next /app/public /app

# Run
CMD ["node", "server.js"]
