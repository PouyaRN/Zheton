describe('Tampered Market Rates Rejection', () => {
  it('rejects tampered /rates.json when signature does not match and shows warning banner', () => {
    cy.clearLocalStorage();
    cy.intercept('GET', '**/rates.json', {
      statusCode: 200,
      body: JSON.stringify({
        version: 2,
        kid: 'k1',
        updatedAt: Date.now(),
        generatedAt: Date.now(),
        rates: { usd: 999999, gold18: 1 },
        coins: [],
        stocks: [],
      }),
    }).as('tamperedRates');

    cy.visit('/');
    cy.wait('@tamperedRates');
    cy.get('[data-testid="market-freshness-state"]', { timeout: 10000 }).should(
      'have.attr',
      'data-verified',
      'false',
    );
    cy.get('[data-testid="market-status-banner"]').should('be.visible');
  });
});

