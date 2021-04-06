package ch.elexis.core.data.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.model.IOrder;
import ch.elexis.core.model.IOrderEntry;
import ch.elexis.core.model.IStockEntry;
import ch.elexis.core.services.IOrderService;
import ch.elexis.data.Artikel;
import ch.elexis.data.BestellungEntry;
import ch.elexis.data.Query;
import ch.elexis.data.StockEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService implements IOrderService {
	private static Logger log = LoggerFactory.getLogger(OrderService.class);
	
	public IOrderEntry findOpenOrderEntryForStockEntry(IStockEntry ise){
		StockEntry se = (StockEntry) ise;
		Artikel article = se.getArticle();
		//20210406js: avoid java.lang.NullPointerException on showing this view
		try {
			Query<BestellungEntry> qre = new Query<BestellungEntry>(BestellungEntry.class);
			qre.add(BestellungEntry.FLD_STOCK, Query.EQUALS, se.getStock().getId());
			qre.add(BestellungEntry.FLD_ARTICLE_TYPE, Query.EQUALS, article.getClass().getName());
			qre.add(BestellungEntry.FLD_ARTICLE_ID, Query.EQUALS, article.getId());
			qre.add(BestellungEntry.FLD_STATE, Query.NOT_EQUAL,
				Integer.toString(BestellungEntry.STATE_DONE));
			List<BestellungEntry> execute = qre.execute();
			if (!execute.isEmpty()) {
				return execute.get(0);
			}
		} catch(NullPointerException e) {
			log.error("findOpenOrderEntryForStockEntry(): NullPointerException most probably on article.getClass().getName()");
		}
		return null;
	}
	
	@Override
	public IOrderEntry addRefillForStockEntryToOrder(IStockEntry ise, IOrder order){
		int current = ise.getCurrentStock();
		int max = ise.getMaximumStock();
		if (max == 0) {
			max = ise.getMinimumStock();
		}
		int toOrder = max - current;
		
		if (toOrder > 0) {
			return order.addEntry(ise.getArticle(), ise.getStock(), ise.getProvider(), toOrder);
		}
		
		return null;
	}
	
}
