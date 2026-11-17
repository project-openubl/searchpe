# PSE rejected-CDR rate high

RS 199-2015 art. 7: rejected CDRs of documents the platform signs as PSE must stay at or under
10% the first year and 5% afterwards, per half-year. The alert warns at 4%.

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$URL/api/admin/v1/pse/rejected-rate" | jq
```

- Find which tenants and codes drive it (SQL on `document` with `emission_mode = 'pse'`, through a
  support session) and help them fix their data; repeated rejections from one tenant may justify
  pausing its PSE mode (`PUT …/pse/mode` back to software, with notice).
- Validation gaps on our side (a rule the engine doesn't check yet) are bugs: add the rule (R3).
