'use strict';

var express = require('express')
  , Primus = require('primus')
  , http = require('http')
  , logger = require('morgan')
  , findme = require('./lib/findme')

var port = process.env.findmeport || 5000
  , app = express()
  , server = http.createServer(app);

server.listen(port);

var primus = new Primus(server, { transformer: 'engine.io' });
findme.init(primus);

app
  .use(logger({ format: 'dev' }))
  .get('/tiles/:z/:x/:y', findme.getTile) // get tiles handler
  .use(express.static(__dirname + '/public')) // serve static files
;

primus.on('connection', function(spark) {
  console.log('socket opened');

  findme.sendAllLocations(spark.id);

  spark.on('data', function(message) {
    console.log('socket message - ' + message.action);

    if (!message.action || !message.data)
      return console.log('malformed socket message, disregarding');

    switch(message.action) {
      case 'updatelocation':
        findme.updateLocation(this.id, message.data);
        break;

      default:
        console.log('socket message received, did not match an action');
        break;
    }
    
  });
});

primus.on('disconnection', function(spark) {
  console.log('socket closed');
  findme.deleteLocation(spark.id);
});

