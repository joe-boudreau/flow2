use('flow2');
db.createCollection('posts');
db.posts.createIndex({ slug: 1 }, { unique: true });
db.posts.createIndex(
    { title: 'text', content: 'text' , tags: 'text' , category: 'text' },
    {weights: { title: 10, category: 5, tags: 3, content: 1 }, name: 'textSearchIndex'}
);
console.log('flow2 DB initialized');
