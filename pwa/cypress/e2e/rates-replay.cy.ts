describe('Market Rates Anti-Replay Protection', () => {
  it('keeps cached newer rates if network returns an older updatedAt timestamp', () => {
    const futureTs = Date.now() + 3600_000;
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem(
          'zheton:verified-rates:v1',
          JSON.stringify({
            schema: 1,
            kid: 'k1',
            updatedAt: futureTs,
            generatedAt: futureTs,
            toman: {
              usd: 91234,
              gold18: 4850000,
            },
          }),
        );
      },
    });

    cy.get('[data-testid="market-freshness-state"]', { timeout: 10000 })
      .should('have.attr', 'data-verified', 'true')
      .and('have.attr', 'data-status-reason', 'replay');
  });
});
