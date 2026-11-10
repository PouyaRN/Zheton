describe('PWA Offline Icon Resilience', () => {
  it('renders all icons when all external network origins are blocked', () => {
    cy.intercept({ url: /^https?:\/\/(?!127\.0\.0\.1|localhost).*/ }, { forceNetworkError: true }).as('externalBlocked');
    cy.visit('/');
    cy.get('svg[data-icon]').should('have.length.greaterThan', 5);
    cy.get('svg[data-icon]').first().should(($svg) => {
      const rect = $svg[0].getBoundingClientRect();
      expect(rect.width).to.be.greaterThan(0);
      expect(rect.height).to.be.greaterThan(0);
    });
  });
});

