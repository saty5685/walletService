import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';

const API = process.env.REACT_APP_API_URL || 'http://localhost:5000/api';

function BlogDetail() {
  const { id } = useParams();
  const [blog, setBlog] = useState(null);

  useEffect(() => {
    axios.get(`${API}/blogs/${id}`).then(res => setBlog(res.data)).catch(() => {
      setBlog({ title: 'Blog Post', content: 'Content not available at the moment.', author: 'Admin', createdAt: new Date().toISOString() });
    });
  }, [id]);

  if (!blog) return <div style={{ padding: '100px', textAlign: 'center' }}>Loading...</div>;

  return (
    <div className="blog-detail">
      <h1>{blog.title}</h1>
      <div className="blog-meta">
        By {blog.author || 'Admin'} | {new Date(blog.createdAt).toLocaleDateString()}
      </div>
      <div className="blog-body">
        {blog.content?.split('\n').map((paragraph, idx) => (
          <p key={idx} style={{ marginBottom: '10px' }}>{paragraph}</p>
        ))}
      </div>
    </div>
  );
}

export default BlogDetail;
