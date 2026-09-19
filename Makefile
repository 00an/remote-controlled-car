.PHONY: dev-frontend dev-backend

dev-frontend:
	cd frontend && npm run dev

dev-backend:
	cd backend && sh mvnw spring-boot:run
