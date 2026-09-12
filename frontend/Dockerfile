# ---------- Dependencies ----------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---------- Build ----------
FROM node:22-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
ARG NEXT_PUBLIC_GOOGLE_MAPS_API_KEY
ENV NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=${NEXT_PUBLIC_GOOGLE_MAPS_API_KEY}
ARG NEXT_PUBLIC_COMPANY_EMAIL
ENV NEXT_PUBLIC_COMPANY_EMAIL=${NEXT_PUBLIC_COMPANY_EMAIL}
ARG NEXT_PUBLIC_COMPANY_PHONE
ENV NEXT_PUBLIC_COMPANY_PHONE=${NEXT_PUBLIC_COMPANY_PHONE}
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---------- Runtime ----------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S bhgroup && adduser -S bhgroup -G bhgroup

COPY --from=build /app/public ./public
COPY --from=build --chown=bhgroup:bhgroup /app/.next/standalone ./
COPY --from=build --chown=bhgroup:bhgroup /app/.next/static ./.next/static

USER bhgroup
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
