const mysql = require('mysql');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const axios = require('axios');
const FormData = require('form-data');
const Path = require('path');
const fs = require('fs');
require('dotenv').config();
const { JWT_SECRET } = require("../config.js");


const connection = mysql.createConnection({
    host: process.env.DB_HOST,
    user: process.env.DB_USER,
    database: process.env.DB_NAME,
    password: process.env.DB_PASS
});

const createUser = async (request, h) => {
    try {
        const {
            name,
            email,
            pass
        } = request.payload;
    
        if (!email || !pass) {
            const response = h.response({
                status: 'fail',
                message: 'Please fill email and password',
              });
              response.code(400);
              return response;
        }

        // cek email di db
        const checkEmailQuery = 'SELECT * FROM table_user WHERE user_email = ?';
        const existingUser = await new Promise((resolve, reject) => {
            connection.query(checkEmailQuery, [email], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve(rows[0]);
                }
            });
        });

        if (existingUser) {
            const response = h.response({
                status: 'fail',
                message: 'Email already exists',
            });
            response.code(400);
            return response;
        }
    
        const hashedPass = await bcrypt.hash(pass, 10);
    
        const query = "INSERT INTO table_user(user_name, user_email, user_pass) VALUES(?, ?, ?)";
    
        await new Promise((resolve, reject) => {
            connection.query(query, [name, email, hashedPass], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
    
        const response = h.response({
            status: 'success',
            message: 'User created successfully',
        });
        response.code(200);
        return response;
    } catch (err) {
        const response = h.response({
          status: 'fail',
          message: err.message,
        });
        response.code(500);
        return response;
    }
}

const loginUser = async (request, h) => {
    const { email, pass } = request.payload;

    try {
        const query = "SELECT * FROM table_user WHERE user_email = ?";

        const user = await new Promise((resolve, reject) => {
            connection.query(query, [email], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve(rows[0]);
                }
            });
        });
        
        if (!user){
            const response = h.response({
                status: 'fail',
                message: 'Account invalid',
            });
            response.code(400);
            return response;
        }
        
        const isPassValid = await bcrypt.compare(pass, user.user_pass);
        
        if (!isPassValid){
            const response = h.response({
                status: 'fail',
                message: 'Account invalid',
            });
            response.code(400);
            return response;
        }
        
        const token = jwt.sign({ userId : user.user_id }, 'JWT_SECRET');
    
        const response = h.response({
            status: 'success',
            message: 'login successful',
            data: { token },
        });
        response.code(200);
        return response;
    } catch (err) {
        const response = h.response({
            status: 'fail',
            message: err.message,
        });
        response.code(500);
        return response;
    }
}

const readUser = async (request, h) => {
    try {
        const token = request.headers.authorization.replace('Bearer ', '');
        let decodedToken;

        try{
            decodedToken = jwt.verify(token, 'JWT_SECRET');
        } catch (err) {
            const response = h.response({
                status: 'missed',
                message: 'User is not authorized!',
            });
            response.code(401);
            return response;
        }

        const userId = decodedToken.userId;

        const query = 'SELECT * FROM table_user WHERE user_id = ?';
        
        const user = await new Promise((resolve, reject) => {
            connection.query(query, [userId], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve(rows[0]);
                }
            });
        });

        if (!user){
            const response = h.response({
                status: 'fail',
                message: 'User is not found!',
            });
            response.code(400);
            return response;
        }

        const { user_pass, ...userData } = user;

        const response = h.response({
            status: 'success',
            message: 'read successful',
            data: userData,
        });
        response.code(200);
        return response;
    } catch (err) {
        const response = h.response({
            status: 'fail',
            message: err.message,
        });
        response.code(500);
        return response;
    }
}

const updateUser = async (request, h) => {
    const { 
        name,
        age,
        gender,
        height,
        weight
    } = request.payload;

    const token = request.headers.authorization.replace('Bearer ', '');
    let decodedToken;

    try{
        decodedToken = jwt.verify(token, 'JWT_SECRET');
    } catch (err) {
        const response = h.response({
            status: 'missed',
            message: 'User is not authorized!',
        });
        response.code(401);
        return response;
    }

    const userId = decodedToken.userId;

    try {
        const query = 'UPDATE table_user SET user_name = ?, user_age = ?, user_gender = ?, user_height = ?, user_weight = ? WHERE user_id = ?';
        
        // will add userId later
        await new Promise((resolve, reject) => {
            connection.query(query, [name, age, gender, height, weight, userId], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });

        const response = h.response({
            status: 'success',
            message: 'update successful',
        });
        response.code(200);
        return response;
    } catch (err) {
        const response = h.response({
            status: 'fail',
            message: err.message,
        });
        response.code(500);
        return response;
    }
}

const deleteUser = async (request, h) => {
    try {
        const token = request.headers.authorization.replace('Bearer ', '');
        let decodedToken;

        try{
            decodedToken = jwt.verify(token, 'JWT_SECRET');
        } catch (err) {
            const response = h.response({
                status: 'missed',
                message: 'User is not authorized!',
            });
            response.code(401);
            return response;
        }

        const userId = decodedToken.userId;
        
         // Delete objects
        const deleteObjects = 'DELETE FROM table_object WHERE user_id = ?';
        await new Promise((resolve, reject) => {
            connection.query(deleteObjects, [userId], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
        
        
        // Delete user
        const query = 'DELETE FROM table_user WHERE user_id = ?';
        await new Promise((resolve, reject) => {
            connection.query(query, [userId], (err, rows, field) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
        const response = h.response({
            status: 'success',
            message: 'Delete successful',
        });
        response.code(200);
        return response;
    } catch (err) {
        const response = h.response({
            status: 'fail',
            message: err.message,
        });
        response.code(500);
        return response;
    }
};
        

module.exports = {
    createUser,
    loginUser,
    readUser,
    updateUser,
    deleteUser,
};
