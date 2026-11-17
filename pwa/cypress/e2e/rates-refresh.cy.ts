describe('Market Rates Manual Refresh Flow', () => {
  it('shows retry button when rates fail initially and loads verified rates upon clicking retry', () => {
    let shouldFail = true;
    cy.clearLocalStorage();
    cy.intercept('GET', '**/rates.json', (req) => {
      if (shouldFail) {
        req.reply({ statusCode: 503, body: 'Service Unavailable' });
      } else {
        req.continue();
      }
    }).as('ratesReq');

    cy.visit('/');
    cy.wait('@ratesReq');
    cy.get('[data-testid="market-retry-button"]', { timeout: 10000 }).should('be.visible');

    shouldFail = false;
    cy.get('[data-testid="market-retry-button"]').click();
    cy.wait('@ratesReq');
    cy.get('[data-testid="market-freshness-state"]', { timeout: 10000 })
      .should('have.attr', 'data-verified', 'true');
  });
});
