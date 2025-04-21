'use strict';

const net = require('net');

const fs = require('fs');
    
const userAgent = "ntrip client Nodejs/1.0.0";

/* please visit https://github.com/geodnet/GEODNET_RTK_SERVICE for more details about GEODNET RTK service */
let username = 'usr';
let password = 'pwd'; /* need to get your own usr/pwd */
let host = 'rtk.geodnet.com';
let port = 2101;
let mount = 'AUTO_ITRF2014'; /* can be AUTO, AUTO_ITRF2020, AUTO_WGS84 */
let lat = 37.398583184829945;
let lon =-121.97869617705001;
let alt = 100.0;
let nsat = 20;
let hdop = 1.0;
let age = 0;
let staid = 0;
/* parse argument */
function parseArgv() {

  const argvs = process.argv.splice(2);
  const args = {};
  for (let i = 0; i < argvs.length; i++) {
    let key = '';
    let val = '';
    if (argvs[i].startsWith('-')) {
      key = argvs[i].replace(/^-*/, '');
    }

    if (key === '') {
      continue;
    }

    const idx = key.indexOf('=');
    if (idx >= 0) {
      val = key.substring(idx + 1);
      key = key.substring(0, idx);
    } else {
      val = argvs[i + 1];
      i += 1;
    }

    args[key] = val;
  }

  for (const key in args) {
    switch (key) {
      case 'host':
        host = args[key];
        break;
      case 'mount':
        mountpoint = args[key];
        break;
      case 'port':
        port = args[key];
        break;
      case 'user':
        username = args[key];
        break;
      case 'password':
        password = args[key];
        break;
      case 'lat':
        lat = args[key];
        break;
      case 'lon':
        lon = args[key];
        break;
      default:
        break;
    }
  }
}
/* generate NMEA GGA */
function generateGGA(latitude, longitude, altitude, numSatellites, hdop, age, staid) {
    const now = new Date();
    const hours = String(now.getUTCHours()).padStart(2, '0');
    const minutes = String(now.getUTCMinutes()).padStart(2, '0');
    const seconds = String(now.getUTCSeconds()).padStart(2, '0');
    const time = `${hours}${minutes}${seconds}.000`;
  
    const latDir = latitude >= 0 ? 'N' : 'S';
    const lonDir = longitude >= 0 ? 'E' : 'W';

    latitude = Math.abs(latitude);
    longitude = Math.abs(longitude);
    
    const latDD = Math.floor(latitude);
    const lonDD = Math.floor(longitude);

    const latMM = (latitude-latDD)*100.0;
    const lonMM = (longitude-lonDD)*100.0;

    const latDDstr = String(latDD).padStart(2,'0');
    const lonDDstr = String(lonDD).padStart(3,'0');
  
    const gga = `$GPGGA,${time},${latDDstr}${latMM.toFixed(4)},${latDir},${lonDDstr}${lonMM.toFixed(4)},${lonDir},1,${numSatellites},${hdop.toFixed(2)},${altitude.toFixed(2)},M,0.0,M,${age.toFixed(2)},${staid}`;
    const checksum = calculateChecksum(gga);
    return `${gga}*${checksum.toString(16).toUpperCase()}`;
}
  
  function calculateChecksum(sentence) {
    let checksum = 0;
    for (let i = 1; i < sentence.length; i++) {
      checksum ^= sentence.charCodeAt(i);
    }
    return checksum;
  }

/* main */

parseArgv();

let time = new Date();
let yyyy = time.getFullYear();
let mm = time.getMonth() + 1;
let dd = time.getDate();
let hh = time.getHours();
let MM = time.getMinutes();
let SS = time.getSeconds();

let fname = `${yyyy}-${mm}-${dd}-${hh}-${MM}-${SS}-${mount}.log`;

let numofbyte = 0;
let is_header = 0;

const writers = fs.createWriteStream(fname, "latin1");

var client = new net.Socket()

client.connect(port, host, function() {
	console.log('Connected');
    const authorization = Buffer.from(
        username + ':' + password,
        'utf8'
      ).toString('base64');
      const data = `GET /${mount} HTTP/1.0\r\nUser-Agent: ${userAgent}\r\nAuthorization: Basic ${authorization}\r\n\r\n`;
      client.write(data);
	/* send GGA to server every 2 seconds */
	const intervalId = setInterval(() => {
		const ggaSentence = generateGGA(lat, lon, alt, nsat, hdop, age, staid);
        client.write(ggaSentence+'\r\n');
	  }, 2000);
});

client.on('data', function(data) {
    numofbyte += data.length;
	console.log('Received: '+data.length+'\tTotal: '+numofbyte);

	time = Date.now();

	writers.write(`$GEOD,${mount},${time},${data.length},`);
	writers.write(data);
	writers.write('\r\n');

});

client.on('close', function() {
	console.log('Connection closed');
	writers.close();
});


setInterval(function() {
}, 2000);

   