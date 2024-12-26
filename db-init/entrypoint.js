use('flow2');
db.createCollection('posts');
db.posts.createIndex({ slug: 1 }, { unique: true });
console.log('flow2 DB initialized');
