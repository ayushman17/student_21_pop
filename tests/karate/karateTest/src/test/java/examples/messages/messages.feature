@ignore
Feature: Messages Test

  Scenario: Send Create LAO message
    Given def demoBaseUrl = 'ws://localhost:9000/organizer/client'
    And def socket = karate.webSocket(demoBaseUrl, null)
    And def txt = '{"method": "publish","id": 1,"params": {"channel": "/root","message": {"data": "eyJvYmplY3QiOiJsYW8iLCJhY3Rpb24iOiJjcmVhdGUiLCJuYW1lIjoiTEFPIiwiY3JlYXRpb24iOjE2MzMwMzU3MjEsIm9yZ2FuaXplciI6Iko5ZkJ6SlY3MEprNWMtaTMyNzdVcTRDbWVMNHQ1M1dEZlVnaGFLMEhwZU09Iiwid2l0bmVzc2VzIjpbXSwiaWQiOiJwX0VZYkh5TXY2c29wSTVRaEVYQmY0ME1PX2VOb3E3Vl9MeWdCZDRjOVJBPSJ9","sender": "J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM=","signature": "ONylxgHA9cbsB_lwdfbn3iyzRd4aTpJhBMnvEKhmJF_niE_pUHdmjxDXjEwFyvo5WiH1NZXWyXG27SYEpkasCA==","message_id": "2mAAevx61TZJi4groVGqqkeLEQq0e-qM6PGmTWuShyY=","witness_signatures": []}},"jsonrpc": "2.0"}'
    When socket.send(txt)
    And def result = socket.listen(5000)
    Then match result == '{"jsonrpc":"2.0","id":1,"result":0}'