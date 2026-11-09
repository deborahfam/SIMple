# Cubacel query catalog

Researched 2026-11-09. The executable catalog is `CubacelQuery`; the USSD dispatcher
accepts this enum instead of arbitrary strings. These are operator-defined codes,
not an exhaustive list of every cellular supplementary service.

## Enabled read-only commands

ETECSA's [personal plans page](https://www.etecsa.cu/es/movil/planes-personales)
(last updated November 6, 2026) publishes:

- `*222#`: main balance, voice/SMS/data resources and line validity.
- `*222*328#`: data plan and validity.
- `*222*266#`: bonuses and USD plans. Keep these separate from main CUP balance.
- `*222*869#`: voice plan and validity.
- `*222*767#`: SMS plan and validity.

ETECSA's [balance/recharge page](https://www.etecsa.cu/es/movil/necesitas-saldo)
also publishes `*222*732#` for recharge status. Its
[official Cubacel channel](https://t.me/s/CubacelResponde/1295) clarifies that this
checks domestic recharges received, remaining recharge capacity, and next eligible
recharge date. Do not describe this as proof of an international top-up arriving.

The catalog is usable by the asynchronous query adapter today. It does not automatically
run all codes on launch or fall back to the other SIM. The UI must first select the
Cubacel subscription. Do not auto-select solely from a carrier display name.

## Additional discovered queries, not enabled

These need confirmation for the selected account type and current operator support:

- `*222*887#`: CubacelTur resources. Published in the
  [tourist service manual](https://www.recargasacuba.com/Content/uploads/tourist/manual_cubacel_tur.pdf)
  and [SuenaCuba FAQ](https://www.suenacuba.com/faq). ETECSA's current
  [tourist page](https://www.etecsa.cu/es/visitantes/cubaceltur) describes newer package
  variants without publishing this query code. Do not assume it applies to a regular prepaid SIM.
- `*111#`: postpaid/institutional resources, reported by
  [Fonoma](https://help.fonoma.com/es/articles/2569882-como-revisar-el-consumo-de-datos-moviles-codigos-utiles-de-la-telefonia-movil-en-cuba).
- `*222*264#`: Plan Amigos status, reported by the same Fonoma reference.
  ETECSA currently documents the manual consultation route through `*133#`, option 4,
  then option 3, rather than confirming that direct code on its plans page.

## Other codes found, excluded from balance dispatch

- `*133#`: interactive purchase/activation menu, confirmed on ETECSA's plans page.
- `*234#`: transfer/PIN/advance service menu, documented on ETECSA's recharge page.
- `*662*<voucher>#`: voucher redemption; `*666`: voice recharge instructions,
  documented on that recharge page. The latter is a voice service, not a USSD response.

These change account state or enter interactive services; they are not balance checks.
Do not execute them as refresh requests. No transfer PINs, vouchers, device identity
queries, or SMS capability checks are collected by this feature.

## Response integration remains separate

Publishing a query code does not document its exact response grammar. The English
`ExampleCarrierParser` is a synthetic example, not a Cubacel parser. Do not wire it to
these commands in a production UI. Cubacel needs command-specific, redacted Spanish
response fixtures before trustworthy extraction of amounts, bundles, dates and SMS
counts. In particular, recharge-limit and SMS responses need result types beyond the
current `BalanceSnapshot`; never force their numeric values into money or data fields.

Use `America/Havana` for a future Cubacel parser's local dates, respecting daylight
saving time. Distinguish line validity, balance validity and each bundle's expiry;
USD plan labels do not mean that the main balance is denominated in USD. No live SIM
query has been executed in this development environment.
