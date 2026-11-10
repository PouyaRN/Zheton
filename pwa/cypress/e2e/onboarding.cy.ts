describe('Onboarding Modal Flow', () => {
  it('completes onboarding on fresh localStorage with SVG icons', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains('مرحله بعد').should('be.visible').click();
    cy.contains('مرحله بعد').should('be.visible').click();
    cy.contains('شروع استفاده از ژتون').should('be.visible').click();
    cy.get('svg[data-icon]').should('have.length.greaterThan', 3);
  });
});
