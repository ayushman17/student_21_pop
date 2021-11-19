function fn() {
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'go';
  }
  var config = {
    env: env,
    ws_url: 'ws://',    // server url
    host: 'localhost',  // host of the server
    port: 0,            // port
    path:  '',          // uri path

    data_dir: '../../../../../data/' // tests' data directory
  }
  if (env === 'go') {
    config.port = 9000;
    config.path = 'organizer/client';
  } else if (env === 'scala') {
    config.port = 8000;
  }
  config.ws_url = `${config.ws_url}${config.host}:${config.port}/${config.path}`;
  return config;
}