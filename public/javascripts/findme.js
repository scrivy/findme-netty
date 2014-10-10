'use strict';

var fs = require('fs')
  , mkdirp = require('mkdirp')
  , http = require('http')

var primus
  , locations = {};

var self = module.exports = {
  init: function(primustocache) {
    primus = primustocache;

    fs.exists(__dirname + '/../public/tiles', function(exists) {
      if (!exists)
        fs.mkdir(__dirname + '/../public/tiles', function(err) {
          if (err) return console.error(err)
        })
    })
  },

  sendAllLocations: function(id) {
    var message = {
      action: 'alllocations',
      data: { locations: locations }
    };

    primus.connections[id].write(message);
  },

  updateLocation: function(id, data) {
    locations[id] = data;

    var message = {
      action: 'updatelocation',
      data: data
    };
    message.data.id = id;

    primus.forEach(function(spark, next) {
      if (spark.id !== id) {
        spark.write(message);
        next();
      } else {
        next();
      }
    }, function(err) {
      if (err) console.error('Primus error - ' + err);
    });
  },

  deleteLocation: function(id) {
    delete locations[id];

    var message = {
      action: 'deletelocation',
      data: { id: id }
    };

    primus.write(message);
  },

  getTile: function(req, res) {
    var tile = req.params.z + '/' + req.params.x + '/' + req.params.y
      , tilefile = __dirname + '/../public/tiles/' + tile;

    fs.exists(tilefile, function(exists) {
      if (exists) {
        fs.createReadStream(tilefile).pipe(res);
      } else {
        mkdirp(__dirname + '/../public/tiles/' + req.params.z + '/' + req.params.x, function(err) {
          if (err) return console.error(err)

          http
            .get('http://78.47.233.251/outdoors/' + tile, function(response) {
              var body = []
              response
                .on('error', console.error)
                .on('data', [].push.bind(body))
                .on('end', function() {
                  body = Buffer.concat(body)
                  fs.writeFile(tilefile, body, function(err) { if (err) console.error(error); console.log('saved tile ' + tile) })
                  res.send(body)
                })
              ;
            })
          .on('error', console.error)
        });
      }
    });
  }
};

  
