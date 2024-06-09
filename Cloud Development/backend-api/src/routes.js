const { 
    createUser, 
    loginUser, 
    updateUser, 
    readUser,  
    deleteUser
} = require('./handler');

const routes = [
    {
        method: 'POST',
        path: '/register',
        handler: createUser,
    }, {
        path: '/test',
        method: 'GET',
        handler: (request, h) => {
            const response = h.response({
                status: 'success',
                message: 'testing',
            });
            response.code(200);
            return response;
        }
    },
    {
        method: 'POST',
        path: '/login',
        handler: loginUser,
    },
    {
        method: 'GET',
        path: '/readUser',
        handler: readUser,
    },
    {
        method: 'PUT',
        path: '/updateUser',
        handler: updateUser
    },
    {
        method: 'DELETE',
        path: '/deleteUser',
        handler: deleteUser
    }
];

module.exports = routes;