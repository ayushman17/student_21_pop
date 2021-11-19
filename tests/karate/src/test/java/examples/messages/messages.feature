Feature: Messages Test

  Background:
    * def socket = karate.webSocket(ws_url, null)

  Scenario: Send Create LAO message
    * def message = 'lao_create/publish.json'
    * def expected = 'lao_create/answer.json'
    * call read('check_answer.feature')
