describe('Signed Market Rates Verification', () => {
  it('verifies bundled ECDSA P-256 signature on /rates.json and loads market quotes', () => {
    cy.visit('/');
    cy.get('[data-testid="market-freshness-state"]', { timeout: 10000 })
      .should('have.attr', 'data-verified', 'true')
      .and(($el) => {
        const count = Number($el.attr('data-quotes-count') || '0');
        expect(count).to.be.greaterThan(10);
      });
  });
});
