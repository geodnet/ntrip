'use strict';

const net = require('net');

const userAgent = "ntrip client Nodejs/1.0.0";

/* please visit https://github.com/geodnet/GEODNET_RTK_SERVICE for more details about GEODNET RTK service */
let host = 'rtk.geodnet.com';
let port = 2101;
let username = '';
let password = '';

/* print usage */
function printUsage() {
  console.log(`${userAgent}

Usage: node ntrip_sourcetable.js [options]

Fetches and prints the Ntrip source table (list of mountpoints) from a caster.

Options:
  --host=<host>       Ntrip caster hostname (default: ${host})
  --port=<port>       Ntrip caster port (default: ${port})
  --user=<user>       Caster account username (optional)
  --password=<pass>   Caster account password (optional)
  -h, --help          Show this help message and exit

See https://github.com/geodnet/GEODNET_RTK_SERVICE for details.`);
}

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

  if ('h' in args || 'help' in args) {
    printUsage();
    process.exit(0);
  }

  for (const key in args) {
    switch (key) {
      case 'host':
        host = args[key];
        break;
      case 'port':
        port = Number(args[key]);
        break;
      case 'user':
        username = args[key];
        break;
      case 'password':
        password = args[key];
        break;
      default:
        break;
    }
  }
}

/* parse the sourcetable body into STR/CAS/NET records, per the Ntrip spec */
function parseSourceTable(body) {
  const lines = body.split('\r\n').filter((line) => line.length > 0 && line !== 'ENDSOURCETABLE');

  const streams = [];
  const casters = [];
  const networks = [];

  for (const line of lines) {
    const fields = line.split(';');
    if (fields[0] === 'STR') {
      streams.push({
        mountpoint: fields[1],
        identifier: fields[2],
        format: fields[3],
        formatDetails: fields[4],
        carrier: fields[5],
        navSystem: fields[6],
        network: fields[7],
        country: fields[8],
        lat: fields[9],
        lon: fields[10],
        nmea: fields[11],
        solution: fields[12],
        generator: fields[13],
        compression: fields[14],
        authentication: fields[15],
        fee: fields[16],
        bitrate: fields[17],
      });
    } else if (fields[0] === 'CAS') {
      casters.push(line);
    } else if (fields[0] === 'NET') {
      networks.push(line);
    }
  }

  return { streams, casters, networks };
}

parseArgv();

const client = new net.Socket();
let response = Buffer.alloc(0);

client.setTimeout(10000);

client.connect(port, host, () => {
  let request = `GET / HTTP/1.0\r\nUser-Agent: ${userAgent}\r\n`;
  if (username || password) {
    const authorization = Buffer.from(`${username}:${password}`, 'utf8').toString('base64');
    request += `Authorization: Basic ${authorization}\r\n`;
  }
  request += `\r\n`;
  client.write(request);
});

client.on('data', (data) => {
  response = Buffer.concat([response, data]);
});

client.on('timeout', () => {
  console.error('Connection timed out');
  client.destroy();
  process.exit(1);
});

client.on('error', (err) => {
  console.error('Connection error:', err.message);
  process.exit(1);
});

client.on('close', () => {
  const text = response.toString('utf8');
  const headerEnd = text.indexOf('\r\n\r\n');
  const statusLine = text.split('\r\n')[0];

  if (!statusLine.startsWith('SOURCETABLE')) {
    console.error(`Unexpected response: ${statusLine}`);
    console.error(text);
    process.exit(1);
  }

  const body = headerEnd >= 0 ? text.slice(headerEnd + 4) : '';
  const { streams, casters, networks } = parseSourceTable(body);

  console.log(`${statusLine}\n`);

  if (networks.length > 0) {
    console.log(`Networks (${networks.length}):`);
    networks.forEach((line) => console.log(`  ${line}`));
    console.log('');
  }

  if (casters.length > 0) {
    console.log(`Casters (${casters.length}):`);
    casters.forEach((line) => console.log(`  ${line}`));
    console.log('');
  }

  console.log(`Mountpoints (${streams.length}):`);
  console.table(streams.map(({ mountpoint, identifier, format, carrier, network, country, nmea }) => (
    { mountpoint, identifier, format, carrier, network, country, nmea }
  )));
});
