@ignore
Feature: Utility Scenario

  Scenario: Publish a message and check the answer
    Given def msg = karate.readAsString(data_dir + message)
    And def exp = karate.readAsString(data_dir + expected)
    When socket.send(msg)
    And def result = socket.listen(5000)
    Then match result == exp