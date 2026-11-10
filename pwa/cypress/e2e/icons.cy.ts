describe('PWA Offline SVG Icons', () => {
  it('renders inline <svg data-icon> elements with non-zero bounding boxes and zero material-symbols', () => {
    cy.visit('/');
    cy.get('svg[data-icon]').should('have.length.greaterThan', 5);
    cy.get('svg[data-icon]').each(($svg) => {
      const rect = $svg[0].getBoundingClientRect();
      expect(rect.width).to.be.greaterThan(0);
      expect(rect.height).to.be.greaterThan(0);
      expect($svg.children().length).to.be.greaterThan(0);
    });
    cy.get('.material-symbols-outlined, .material-symbols-filled').should('have.length', 0);
  });
});

