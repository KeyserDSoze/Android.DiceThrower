/**
 * D&D 5e 2014 SRD Wizard reference, separate from the game-agnostic dice engine.
 * Names are the SRD Wizard class spell list (not every proprietary PHB spell).
 * A name in this catalogue NEVER grants a known or prepared spell; only spells
 * requiring a numeric throw have an opt-in dice preset.
 * Source: https://5e.d20srd.org/srd/spellLists/wizardSpells.htm
 */
export const WIZARD_2014_SRD_SPELLS = [
  ['Acid Splash','Chill Touch','Dancing Lights','Fire Bolt','Light','Mage Hand','Mending','Message','Minor Illusion','Poison Spray','Prestidigitation','Ray of Frost','Shocking Grasp','True Strike'],
  ['Alarm','Burning Hands','Charm Person','Color Spray','Comprehend Languages','Detect Magic','Disguise Self','Expeditious Retreat','False Life','Feather Fall','Find Familiar','Floating Disk','Fog Cloud','Grease','Hideous Laughter','Identify','Illusory Script','Jump','Longstrider','Mage Armor','Magic Missile','Protection from Evil and Good','Shield','Silent Image','Sleep','Thunderwave','Unseen Servant'],
  ['Acid Arrow','Alter Self','Arcane Lock',"Arcanist's Magic Aura",'Blindness/Deafness','Blur','Continual Flame','Darkness','Darkvision','Detect Thoughts','Enlarge/Reduce','Flaming Sphere','Gentle Repose','Gust of Wind','Hold Person','Invisibility','Knock','Levitate','Locate Object','Magic Mouth','Magic Weapon','Mirror Image','Misty Step','Ray of Enfeeblement','Rope Trick','Scorching Ray','See Invisibility','Shatter','Spider Climb','Suggestion','Web'],
  ['Animate Dead','Bestow Curse','Blink','Clairvoyance','Counterspell','Dispel Magic','Fear','Fireball','Fly','Gaseous Form','Glyph of Warding','Haste','Hypnotic Pattern','Lightning Bolt','Magic Circle','Major Image','Phantom Steed','Protection from Energy','Remove Curse','Sending','Sleet Storm','Slow','Stinking Cloud','Tiny Hut','Tongues','Vampiric Touch','Water Breathing'],
  ['Arcane Eye','Banishment','Black Tentacles','Blight','Confusion','Conjure Minor Elementals','Control Water','Dimension Door','Fabricate','Faithful Hound','Fire Shield','Greater Invisibility','Hallucinatory Terrain','Ice Storm','Phantasmal Killer','Polymorph','Private Sanctum','Resilient Sphere','Secret Chest','Stone Shape','Stoneskin','Wall of Fire'],
  ['Animate Objects','Arcane Hand','Cloudkill','Cone of Cold','Conjure Elemental','Contact Other Plane','Creation','Dominate Person','Dream','Geas','Hold Monster','Legend Lore','Mislead','Modify Memory','Passwall','Planar Binding','Seeming','Telekinesis','Telepathic Bond','Teleportation Circle','Wall of Force','Wall of Stone'],
  ['Chain Lightning','Circle of Death','Contingency','Create Undead','Disintegrate','Eyebite','Flesh to Stone','Freezing Sphere','Globe of Invulnerability','Guards and Wards','Instant Summons','Irresistible Dance','Magic Jar','Mass Suggestion','Move Earth','Programmed Illusion','Sunbeam','True Seeing','Wall of Ice'],
  ['Arcane Sword','Delayed Blast Fireball','Etherealness','Finger of Death','Forcecage','Magnificent Mansion','Mirage Arcane','Plane Shift','Prismatic Spray','Project Image','Reverse Gravity','Sequester','Simulacrum','Symbol','Teleport'],
  ['Antimagic Field','Antipathy/Sympathy','Clone','Control Weather','Demiplane','Dominate Monster','Feeblemind','Incendiary Cloud','Maze','Mind Blank','Power Word Stun','Sunburst'],
  ['Astral Projection','Foresight','Gate','Imprisonment','Meteor Swarm','Power Word Kill','Prismatic Wall','Shapechange','Time Stop','True Polymorph','Weird','Wish'],
];

/**
 * Cantrip attack rolls have a spell attack and damage Part; a natural d20=20
 * adds ONLY another damage dice pool. Save spells have damage-only Rolls.
 */
export const WIZARD_2014_CANTRIP_DICE = [
  {name:'Chill Touch', die:8, kind:'attack'},
  {name:'Ray of Frost', die:8, kind:'attack'},
  {name:'Shocking Grasp', die:8, kind:'attack'},
  {name:'Acid Splash', die:6, kind:'save', save:'Dex'},
  {name:'Poison Spray', die:12, kind:'save', save:'Con'},
];

/**
 * Common SRD spells with dice or a class spellcasting CHECK. Rows intentionally
 * exclude utility spells without a numeric roll (Shield, Invisibility, etc.).
 * A save rolls only damage; targets roll their own saves and the table handles
 * half/zero damage, concentrations and repeated effects. Slot upcasts are
 * separately enumerated where supported by 'upcast' and are player-selected.
 */
export const WIZARD_2014_DICE_SPELLS = [
  {level:1,name:'Burning Hands',dice:'3d6',kind:'save',save:'Dex',upcast:{die:6,baseDice:3}},
  {level:1,name:'Color Spray',dice:'6d10',kind:'pool'},
  {level:1,name:'False Life',dice:'1d4+4',kind:'pool'},
  {level:1,name:'Sleep',dice:'5d8',kind:'pool',upcast:{die:8,baseDice:5,perSlot:2}},
  {level:1,name:'Thunderwave',dice:'2d8',kind:'save',save:'Con',upcast:{die:8,baseDice:2}},
  {level:2,name:'Acid Arrow',dice:'4d4',kind:'attack',extra:'2d4 (end of next turn if hit; separate roll)'},
  {level:2,name:'Acid Arrow · subsequent damage (if hit)',dice:'2d4',kind:'pool',listedAs:'Acid Arrow'},
  {level:2,name:'Enlarge/Reduce · enlarge bonus (if hit)',dice:'1d4',kind:'pool',listedAs:'Enlarge/Reduce'},
  {level:2,name:'Flaming Sphere',dice:'2d6',kind:'save',save:'Dex'},
  {level:2,name:'Mirror Image · redirect attack (1d20)',dice:'1d20',kind:'pool',listedAs:'Mirror Image'},
  {level:2,name:'Shatter',dice:'3d8',kind:'save',save:'Con',upcast:{die:8,baseDice:3}},
  {level:3,name:'Blink · fade (11+ on d20)',dice:'1d20',kind:'pool',listedAs:'Blink'},
  {level:3,name:'Counterspell · ability check (if required)',dice:'1d20+{Intelligence}',kind:'check',listedAs:'Counterspell'},
  {level:3,name:'Dispel Magic · ability check (if required)',dice:'1d20+{Intelligence}',kind:'check',listedAs:'Dispel Magic'},
  {level:3,name:'Fireball',dice:'8d6',kind:'save',save:'Dex',upcast:{die:6,baseDice:8}},
  {level:3,name:'Glyph of Warding · explosive runes',dice:'5d8',kind:'save',save:'Dex',listedAs:'Glyph of Warding'},
  {level:3,name:'Lightning Bolt',dice:'8d6',kind:'save',save:'Dex',upcast:{die:6,baseDice:8}},
  {level:3,name:'Vampiric Touch',dice:'3d6',kind:'attack'},
  {level:4,name:'Black Tentacles',dice:'3d6',kind:'save',save:'Dex'},
  {level:4,name:'Blight',dice:'8d8',kind:'save',save:'Con',upcast:{die:8,baseDice:8}},
  {level:4,name:'Fire Shield · retaliation (when hit)',dice:'2d8',kind:'pool',listedAs:'Fire Shield'},
  {level:4,name:'Ice Storm',dice:'2d8+4d6',kind:'save',save:'Dex'},
  {level:4,name:'Phantasmal Killer · ongoing (if eligible)',dice:'4d10',kind:'save',save:'Wis',listedAs:'Phantasmal Killer'},
  {level:4,name:'Wall of Fire · on exposure',dice:'5d8',kind:'save',save:'Dex',listedAs:'Wall of Fire'},
  {level:5,name:'Arcane Hand · clenched fist',dice:'4d8',kind:'attack',listedAs:'Arcane Hand'},
  {level:5,name:'Cloudkill',dice:'5d8',kind:'save',save:'Con',upcast:{die:8,baseDice:5}},
  {level:5,name:'Cone of Cold',dice:'8d8',kind:'save',save:'Con',upcast:{die:8,baseDice:8}},
  {level:5,name:'Telekinesis · contested check',dice:'1d20+{Intelligence}',kind:'check',listedAs:'Telekinesis'},
  {level:6,name:'Chain Lightning',dice:'10d8',kind:'save',save:'Dex'},
  {level:6,name:'Circle of Death',dice:'8d6',kind:'save',save:'Con'},
  {level:6,name:'Disintegrate',dice:'10d6+40',kind:'save',save:'Dex'},
  {level:6,name:'Freezing Sphere',dice:'10d6',kind:'save',save:'Con'},
  {level:6,name:'Sunbeam',dice:'6d8',kind:'save',save:'Con'},
  {level:7,name:'Arcane Sword',dice:'3d10',kind:'attack'},
  {level:7,name:'Delayed Blast Fireball · initial blast',dice:'12d6',kind:'save',save:'Dex',listedAs:'Delayed Blast Fireball'},
  {level:7,name:'Finger of Death',dice:'7d8+30',kind:'save',save:'Con'},
  {level:8,name:'Feeblemind',dice:'4d6',kind:'save',save:'Int'},
  {level:8,name:'Incendiary Cloud',dice:'10d8',kind:'save',save:'Dex'},
  {level:8,name:'Sunburst',dice:'12d6',kind:'save',save:'Con'},
  {level:9,name:'Meteor Swarm',dice:'20d6+20d6',kind:'save',save:'Dex'},
  {level:9,name:'Weird · ongoing (if eligible)',dice:'4d10',kind:'save',save:'Wis',listedAs:'Weird'},
];
