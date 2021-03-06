package org.openstreetmap.josm.plugins.ods.builtenvironment;

import java.util.Set;

import org.openstreetmap.josm.plugins.ods.entities.Entity;

public interface Building extends Entity {
//    public void setGeometry(Geometry geometry);
	public City getCity();
	
	/**
	 * Return the building type of the building, as used in OSM.
	 * For example "apartments" or "office"
	 * 
	 * @return
	 */
	public String getBuildingType();
	
	/**
	 * Return the address information associated with this building.
	 * 
	 * @return null if no address is associated with the building
	 */
	public Address getAddress();
	
	/**
	 * Return the address nodes associated with this building.
	 * @return empty collection if no address nodes are associated with this building.
	 */
    public Set<AddressNode> getAddressNodes();
    
//    public void setBlock(Block block);
//	public Block getBlock();
	public Set<Building> getNeighbours();
	public void addNeighbour(Building building);

	
	/**
	 * Check is the full area of this building has been loaded.
	 * This is true if the building is completely covered by the
	 * downloaded area.
	 * 
	 * @return
	 */
    public void setIncomplete(boolean incomplete);

	public boolean isUnderConstruction();
	
	public String getStartDate();
}
