# Design Patterns

## 1. Repository Pattern
- UserRepository extends JpaRepository which handles all database operations
- The service layer doesn't need to know anything about how data is stored, it just calls the repository

## 2. Singleton Pattern
- All @Service and @Repository beans in Spring are singletons by default
- Spring creates one instance and reuses it everywhere it's injected

## 3. Builder Pattern
- Lomboks Builder is used on entities to avoid big constructors with lots of parameters
- Used when creating a new user in dbinitializer and in the services