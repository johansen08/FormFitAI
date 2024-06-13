
# FormFit Backend API Documentation

Welcome to the documentation for the FormFit backend API. Below are the endpoints available for interacting with user data and profiles.

## Base URL
https://formfit-backend-api-firestore-jgyozsb3oq-uc.a.run.app


## Endpoints

### 1. Test Endpoint

**URL:** `/test`  
**Method:** `GET`  
**Description:** Endpoint to check server status.

**Response:**
```json
{
  "status": "success",
  "message": "testing"
}
```


### 2. Register

**URL:** `/register`  
**Method:** `POST`  
**Description:** Register a new user.

**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "password": "securepassword123"
}

```
**Response:**
```json
{
  "status": "success",
  "message": "User registered successfully",
  "user": {
    "id": "12345",
    "email": "john.doe@example.com",
    "name": "John Doe"
  }
}
```

### 3. User Login

**URL:** `/login`  
**Method:** `POST`  
**Description:** Login with existing credentials.

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "securepassword123"
}

```
**Response:**
```json
{
  "status": "success",
  "message": "Login successful",
  "user": {
    "id": "12345",
    "email": "john.doe@example.com",
    "name": "John Doe",
    "token": "abcdefg12345"
  }
}
```

### 4. Update User

**URL:** `/users/{id}`  
**Method:** `POST`  
**Description:** Update user information.
**Path Parameters:** `{id}` - USER ID

**Request Body:**
```json
{
  "name": "Johnny Doe",
  "email": "johnny.doe@example.com",
  "password": "newsecurepassword123"
}
```
**Response:**
```json
{
  "status": "success",
  "message": "User updated successfully"
}
```

### 5. Get User

**URL:** `/users/{id}`  
**Method:** `GET`  
**Description:** Retrieve user information.
**Path Parameters:** `{id}` - USER ID

**Response:**
```json
{
  "status": "success",
  "data": {
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
}
```

### 6. Delete User

**URL:** `/users/{id}`  
**Method:** `DELETE`  
**Description:** Delete user account.
**Path Parameters:** `{id}` - USER ID

**Response:**
```json
{
  "status": "success",
  "message": "User deleted successfully"
}
```

### 7. Update Profile

**URL:** `/profile`  
**Method:** `PUT`  
**Description:** Update user profile information.

**Request Body:**
```json
{
  "name": "Johnny Doe",
  "gender": "Male",
  "weight": "70kg",
  "height": "175cm"
}

```
**Response:**
```json
{
  "status": "success",
  "message": "Profile updated successfully"
}
```

### 8. Get Profile

**URL:** `/profile`  
**Method:** `GET`  
**Description:** Retrieve user profile information

**Response:**
```json
{
  "status": "success",
  "data": {
    "name": "Johnny Doe",
    "email": "johnny.doe@example.com",
    "gender": "Male",
    "weight": "70kg",
    "height": "175cm"
  }
}
```

## Error Responses

**Status Code: 400**
**Response:**
```json
{
  "status": "Fail",
  "message": "Invalid email or password"
}
```

**Status Code: 404**
**Response:**
```json
{
  "status": "Fail",
  "message": "User not found"
}
```

**Status Code: 500**
**Response:**
```json
{
  "status": "Fail",
  "message": "Internal Server Error"
}
```