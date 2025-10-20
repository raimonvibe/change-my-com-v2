/**
 * Script to create test fixture files
 * Run with: node create-fixtures.js
 */

const fs = require('fs');
const path = require('path');

// Create minimal valid image files

// 1x1 transparent PNG (Base64)
const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
fs.writeFileSync(
  path.join(__dirname, 'test-image.png'),
  Buffer.from(pngBase64, 'base64')
);

// 1x1 transparent GIF (Base64)
const gifBase64 = 'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
fs.writeFileSync(
  path.join(__dirname, 'test-image.gif'),
  Buffer.from(gifBase64, 'base64')
);

// Minimal JPEG (1x1 white pixel)
const jpegBase64 = '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA';
fs.writeFileSync(
  path.join(__dirname, 'test-image.jpg'),
  Buffer.from(jpegBase64, 'base64')
);

// Invalid text file
fs.writeFileSync(
  path.join(__dirname, 'invalid-file.txt'),
  'This is not an image file'
);

// Large file (9MB) - for size validation tests
const largeBuffer = Buffer.alloc(9 * 1024 * 1024);
// Fill with JPEG header to make it a "valid" JPEG (but large)
largeBuffer[0] = 0xFF;
largeBuffer[1] = 0xD8;
largeBuffer[2] = 0xFF;
fs.writeFileSync(
  path.join(__dirname, 'large-file.jpg'),
  largeBuffer
);

console.log('✅ Test fixtures created successfully!');
console.log('Created files:');
console.log('  - test-image.png (< 1KB)');
console.log('  - test-image.jpg (< 1KB)');
console.log('  - test-image.gif (< 1KB)');
console.log('  - invalid-file.txt');
console.log('  - large-file.jpg (9MB)');
