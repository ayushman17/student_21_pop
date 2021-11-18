function fn() {    
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'scala';
    //env = 'go'
  }
  karate.log('karate.env system property is set to', env);
  var config = {
    env: env,
    host: 'hostname',    //Host of the server
    port: 0000,  //Port 
    path:  'URI path', //URI Path
    pk: 'PK orgnizer', //PK of the oranizer 
    wsUrl: 'ws/url/port/path', //Server url 
    timeout: 5000, //Timeout for websocket responce
    serverCmd: 'Command to launch the server', // Cmd to launch the server
    serverDIR: 'Path to server source directory',
    timeToLaunch: 5, // Time to wait for server startup in seconds
    args: [],
  }
  if (env == 'go') {
    // customize
     config.pk = 'g6XxoDTcz2tQZLjiK6zK24foSLSxU5P5tUYlKqhedCo=';
     config.host = '127.0.0.1';
     config.port = 9000 ;
     config.path = 'organizer/client';
     config.wsUrl = `ws://${config.host}:${config.port}/${config.path}`;
     //Directory to launch the server from
     config.serverDIR = '/mnt/c/Users/Mohamed/GolandProjects/student_21_pop/be1-go' ;
     config.serverCmd = `make pop; ./pop organizer --pk ${pk} serve`;
      
  
  } else if (env == 'scala') {
    // customize
    config.host= '127.0.0.1';
    config.port= 8000;
    config.path= '';
    config.wsUrl= `ws://${config.host}:${config.port}/${config.path}`;
    config.serverDIR = 'C:\\Users\\Mohamed\\GolandProjects\\student_21_pop\\be2-scala\\';
    var pathConfig = config.serverDIR + 'src\\main\\scala\\ch\\epfl\\pop\\config';
    config.serverCmd = `sbt -Dscala.config=${pathConfig} run`;
    config.timeToLaunch = 38
  
  }
  return config;
}