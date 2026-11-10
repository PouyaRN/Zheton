describe('Stale & Unverified Market Rates Banner', () => {
  it('displays the market status banner when /rates.json is unreachable and no cache exists', () => {
    cy.clearLocalStorage();
    cy.intercept('GET', '**/rates.json', { statusCode: 503, body: 'Unavailable' }).as('ratesFail');
    cy.visit('/');
    cy.wait('@ratesFail');
    cy.get('[data-testid="market-status-banner"]', { timeout: 10000 }).should('be.visible');
  });
});

