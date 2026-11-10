describe('Market Rates Anti-Replay Protection', () => {
  it('keeps cached newer rates if network returns an older updatedAt timestamp', () => {
    const futureTs = Date.now() + 3600_000;
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem(
          'zheton_verified_market_v2',
          JSON.stringify({
            updatedAt: futureTs,
            generatedAt: futureTs,
            quotes: [
              {
                id: 'usd',
                titleFa: 'دلار آزاد',
                symbol: 'USD',
                kind: 'FIAT',
                unitFa: 'دلار',
                priceToman: 91234,
                change24hPct: 0,
              },
            ],
          }),
        );
      },
    });

    cy.get('[data-testid="market-freshness-state"]', { timeout: 10000 })
      .should('have.attr', 'data-verified', 'true')
      .and('have.attr', 'data-quotes-count', '1');
  });
});

