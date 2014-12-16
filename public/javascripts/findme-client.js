var map = L.map('map').setView([38.55, -121.74], 13);

//L.tileLayer('http://{s}.tile.cloudmade.com/e1d37bab0aaf4f67b0af332838f24a73/997/256/{z}/{x}/{y}.png', {
//  attribution: 'Map data',
//  maxZoom: 18
//}).addTo(map);

//L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
//L.tileLayer('http://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}.png').addTo(map);
L.tileLayer('/tiles/{z}/{x}/{y}.png').addTo(map);

map.locate({setView: true, maxZoom: 18});

var mymarker = L.marker([0, 0], {
  icon: L.icon({
    iconUrl: 'images/mymarker.png',
    iconSize: [25, 41],
    iconAnchor: [12, 40]
  }),
  alt: "Me!"
}).addTo(map)
  , mycircle = L.circle([0, 0], 50, {
    fillOpacity: 0.5
  }).addTo(map);

var everyone = {};

function fadeOutOldMarkers(everyone) {
  Object.keys(everyone)
    .forEach(function(id) {
      var person = everyone[id]
        , opacity = person.circle.options.opacity;

      if (opacity > 0) {
        person.circle.setStyle({ opacity: opacity - 0.05});
        person.marker.setOpacity(person.marker.options.opacity - 0.1)
      } else {
        map.removeLayer(person.circle);
        map.removeLayer(person.marker);
        delete everyone[id];
      }
    })
   ;
}

setInterval(fadeOutOldMarkers, 15000, everyone);

var ws = new WebSocket('ws://' + window.location.host + '/ws');

ws.onmessage = function(event) {
    try {
        var message = JSON.parse(event.data);
    } catch(e) {
        console.error(e.message);
        console.error('raw message: ');
        console.error(event);
        return;
    }
    console.log(message);

  switch(message.action) {
    case 'allLocations':
      var locations = message.data.locations;

      Object.keys(locations)
        .forEach(function(id) {
          everyone[id] = {
            marker: L.marker(locations[id].latlng).addTo(map),
            circle: L.circle(locations[id].latlng, locations[id].accuracy).addTo(map),
            line: L.polyline([mymarker.getLatLng(), locations[id].latlng]).addTo(map)
          };
        })
      ;

      break;
    case 'updateLocation':
      var location = message.data;
      if (everyone[location.id]) {
        everyone[location.id].marker
          .setLatLng(location.latlng)
          .setOpacity(1)
        everyone[location.id].circle
          .setLatLng(location.latlng)
          .setRadius(location.accuracy)
          .setStyle({opacity: 0.5})
        everyone[location.id].line
          .setLatLngs([
            mymarker.getLatLng(),
            location.latlng
          ])
        ;
      } else {
        everyone[location.id] = {
          marker: L.marker(location.latlng).addTo(map),
          circle: L.circle(location.latlng, location.accuracy).addTo(map),
          line: L.polyline([mymarker.getLatLng(), location.latlng]).addTo(map)
        };
      }

      break;
  }
};

if (navigator.geolocation) {
  var geo_options = {
    enableHighAccuracy: true
  };

  function geo_success(position) {
    console.log('got a fix');

    var data = {
      latlng: [position.coords.latitude, position.coords.longitude],
      accuracy: Math.ceil(position.coords.accuracy)
    };

    ws.send(JSON.stringify({ action: 'updateLocation', data: data}));

    mymarker.setLatLng(data.latlng);
    mycircle
      .setLatLng(data.latlng)
      .setRadius(position.coords.accuracy)
    ;

    Object.keys(everyone)
      .forEach(function(id) {
        everyone[id].line
          .setLatLngs([
            data.latlng,
            everyone[id].marker.getLatLng()
          ])
      })
    ;
  }

  function geo_error() {
    console.log('geolocation error');
  }

  navigator.geolocation.watchPosition(geo_success, geo_error, geo_options);
}
