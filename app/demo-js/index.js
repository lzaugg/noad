const fastify = require('fastify');
const app = fastify();
const path = require('path');
const port = process.env.PORT || 3000;

app.register(require('@fastify/static'), {
  root: path.join(__dirname, '/node_modules/reveal.js/'),
  prefix: '/revealjs',
  decorateReply: false
})


app.register(require('@fastify/static'), {
  root: path.join(__dirname, 'public')
})

app.get('/api/dude', async (req, reply) => {
  return {
    text: 'hey dude...' 
  };
});

app.listen({ port, host: '0.0.0.0' }).then(() => {
  console.log(`Server running at http://0.0.0.0:${port}/`);
});
