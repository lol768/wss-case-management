var EventEmitter = require('events');

/**
 * Simple EventEmitter for sending messages between tasks - useful for telling
 * one watching task that something's changed, e.g. to tell the service worker watcher
 * that styles and/or scripts have changed.
 */
module.exports = new EventEmitter();