package ch.elexis.core.jpa.entitymanger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import javax.persistence.EntityManager;

import org.junit.Test;

import ch.elexis.core.jpa.entities.Kontakt;
import ch.elexis.core.services.IElexisEntityManager;
import ch.elexis.core.utils.OsgiServiceUtil;

public class InitPersistenceUnit {
	
	@Test
	public void getEntityManger(){
		Optional<IElexisEntityManager> elexisEntityManager =
			OsgiServiceUtil.getService(IElexisEntityManager.class);
		assertTrue(elexisEntityManager.isPresent());
		EntityManager em = (EntityManager) elexisEntityManager.get().getEntityManager();
		assertNotNull(em);
		em.close();
		OsgiServiceUtil.ungetService(elexisEntityManager.get());
	}
	
	@Test
	public void createKontakt(){
		Optional<IElexisEntityManager> elexisEntityManager =
			OsgiServiceUtil.getService(IElexisEntityManager.class);
		EntityManager em = (EntityManager) elexisEntityManager.get().getEntityManager();
		em.getTransaction().begin();
		assertNotNull(em);
		Kontakt kontakt = new Kontakt();
		kontakt.setDescription1("test");
		em.persist(kontakt);
		em.getTransaction().commit();
		assertNotNull(kontakt.getId());
		String id = kontakt.getId();
		// close and load in new EntityManger
		em.close();
		em = (EntityManager) elexisEntityManager.get().getEntityManager();
		Kontakt loaded = em.find(Kontakt.class, id);
		assertNotNull(loaded);
		assertEquals(kontakt.getId(), loaded.getId());
		em.remove(loaded);
		assertFalse(em.contains(loaded));
		em.close();
		OsgiServiceUtil.ungetService(elexisEntityManager.get());
	}
}
