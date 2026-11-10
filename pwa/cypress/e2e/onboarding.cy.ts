describe('Onboarding Modal Flow', () => {
  it('completes onboarding on fresh localStorage with SVG icons', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains('شروع').should('be.visible');
    cy.get('svg[data-icon]').should('have.length.greaterThan', 3);
  });
});
