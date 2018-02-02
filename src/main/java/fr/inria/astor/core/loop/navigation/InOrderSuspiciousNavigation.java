package fr.inria.astor.core.loop.navigation;

import java.util.Comparator;
import java.util.List;

import fr.inria.astor.core.entities.ModificationPoint;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.SuspiciousModificationPoint;

/**
 * 
 * @author Matias Martinez
 *
 */
public class InOrderSuspiciousNavigation implements SuspiciousNavigationStrategy {

	/**
	 * same order the MP are referenced by the variant.
	 */
	@Override
	public List<ModificationPoint> getSortedModificationPointsList(ProgramVariant variant) {

		List<ModificationPoint> ss = variant.getModificationPoints();
		ss.sort(new Comparator<ModificationPoint>() {

			@Override
			public int compare(ModificationPoint o1, ModificationPoint o2) {
				SuspiciousModificationPoint s1 = (SuspiciousModificationPoint) o1;
				SuspiciousModificationPoint s2 = (SuspiciousModificationPoint) o2;

				return Double.compare(s2.getSuspicious().getSuspiciousValue(), s1.getSuspicious().getSuspiciousValue());
			}
		});
		return ss;
	}

}
