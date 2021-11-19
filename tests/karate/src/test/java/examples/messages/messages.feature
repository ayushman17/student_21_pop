Feature: Messages Test

  Background:
    * def socket = karate.webSocket(ws_url, null)

  Scenario: Send Create LAO message
    Given def message = 'lao_create/publish.json'
    And def expected = 'lao_create/answer.json'
    Then call read('send_and_check.feature')
